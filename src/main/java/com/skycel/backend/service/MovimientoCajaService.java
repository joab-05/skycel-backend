package com.skycel.backend.service;

import com.skycel.backend.domain.entity.CatMotivo;
import com.skycel.backend.domain.entity.Caja;
import com.skycel.backend.domain.entity.CuentaPorCobrar;
import com.skycel.backend.domain.entity.MovimientoCaja;
import com.skycel.backend.domain.entity.OrdenServicio;
import com.skycel.backend.domain.entity.OrdenServicioAnticipo;
import com.skycel.backend.domain.entity.PagoCuenta;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.entity.Venta;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.caja.MovimientoCajaRequestDto;
import com.skycel.backend.dto.caja.MovimientoCajaResponseDto;
import com.skycel.backend.dto.caja.SaldoCajaResponseDto;
import com.skycel.backend.repository.CajaRepository;
import com.skycel.backend.repository.CatMotivoRepository;
import com.skycel.backend.repository.MovimientoCajaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.skycel.backend.service.MotivoCajaService.ABONO_CUENTA;
import static com.skycel.backend.service.MotivoCajaService.ANTICIPO_SERVICIO;
import static com.skycel.backend.service.MotivoCajaService.CANCELACION_VENTA;
import static com.skycel.backend.service.MotivoCajaService.DEVOLUCION_ANTICIPO;
import static com.skycel.backend.service.MotivoCajaService.CAT_PERSONAL;
import static com.skycel.backend.service.MotivoCajaService.ENTRADA;
import static com.skycel.backend.service.MotivoCajaService.SALIDA;
import static com.skycel.backend.service.MotivoCajaService.VENTA;

/**
 * Movimientos de efectivo de las cajas.
 *  - Automáticos: una venta en efectivo registra una entrada; cancelarla registra la salida que la revierte.
 *  - Manuales: fondo inicial, gastos, retiros, etc., con observaciones libres.
 * El saldo de una caja es la suma de sus entradas menos la suma de sus salidas.
 */
@Service
@RequiredArgsConstructor
public class MovimientoCajaService {

    private final MovimientoCajaRepository movimientoCajaRepository;
    private final CajaRepository           cajaRepository;
    private final CatMotivoRepository      catMotivoRepository;
    private final UsuarioRepository        usuarioRepository;

    // ── Movimientos manuales ─────────────────────────────────────────────────

    @Transactional
    public MovimientoCajaResponseDto registrarManual(Integer idCaja, MovimientoCajaRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Caja caja = buscarCaja(idCaja);
        validarAcceso(usuario, caja);

        CatMotivo motivo = catMotivoRepository.findById(dto.getIdmotivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Motivo no encontrado: " + dto.getIdmotivo()));
        if (!Boolean.TRUE.equals(motivo.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El motivo '" + motivo.getNombre() + "' está desactivado.");
        }
        if (MotivoCajaService.esDelSistema(motivo.getNombre())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El motivo '" + motivo.getNombre() + "' lo registra el sistema automáticamente.");
        }

        Usuario admin = null;
        if (motivo.getCatSat() != null && motivo.getCatSat() == CAT_PERSONAL) {
            if (!esAdmin(usuario)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Los movimientos de tipo Personal solo los puede autorizar un ROOT o ADMIN.");
            }
            admin = usuario;
        }

        if (motivo.getTipoMov() == SALIDA) {
            BigDecimal saldo = saldoDe(caja.getIdCaja());
            if (dto.getMonto().compareTo(saldo) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Saldo insuficiente en la caja. Disponible: $" + saldo + ", solicitado: $" + dto.getMonto());
            }
        }

        return toDto(guardar(caja, usuario, admin, motivo, dto.getMonto(), dto.getObservaciones(), null));
    }

    // ── Movimientos automáticos (los llama VentaService en la misma transacción) ──

    /** Entrada por una venta en efectivo. Si fue a crédito, solo entra lo abonado. */
    @Transactional
    public void registrarVentaEfectivo(Venta venta, Usuario vendedor) {
        BigDecimal monto = venta.getMontoAbonado() != null
                ? venta.getMontoAbonado().min(venta.getTotal())
                : venta.getTotal();
        registrarEntradaVenta(venta, vendedor, monto);
    }

    /** Entrada por la parte en efectivo de una venta (p. ej. el efectivo de un pago Mixto). */
    @Transactional
    public void registrarEntradaVenta(Venta venta, Usuario vendedor, BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) return;
        guardar(venta.getCaja(), vendedor, null, motivoSistema(VENTA), monto, referenciaVenta(venta.getIdventa()), null);
    }

    /**
     * Salida que revierte la entrada de una venta cancelada. Si la venta no generó movimiento
     * (no fue en efectivo, o es anterior a este módulo) no hay nada que revertir.
     */
    @Transactional
    public void registrarCancelacionVenta(Venta venta, Usuario usuario) {
        CatMotivo motivoVenta = motivoSistema(VENTA);
        movimientoCajaRepository
                .findFirstByMotivo_IdmotivoAndObservaciones(motivoVenta.getIdmotivo(), referenciaVenta(venta.getIdventa()))
                .ifPresent(original -> guardar(original.getCaja(), usuario, null, motivoSistema(CANCELACION_VENTA),
                        original.getMonto(), "Cancelación de venta #" + venta.getIdventa(), original.getIdmovimiento()));
    }

    /**
     * Entrada por un abono en efectivo a una cuenta por cobrar. Si no se indica caja se usa la
     * caja principal de la tienda del usuario; si tampoco hay, se pide indicarla.
     */
    @Transactional
    public void registrarAbonoCuenta(Integer idCaja, CuentaPorCobrar cuenta, PagoCuenta pago, Usuario usuario) {
        Caja caja = idCaja != null ? buscarCaja(idCaja) : cajaPrincipalDe(usuario);
        validarAcceso(usuario, caja);
        guardar(caja, usuario, null, motivoSistema(ABONO_CUENTA), pago.getMonto(),
                "Abono a cuenta " + cuenta.getNoFactura() + " (pago #" + pago.getIdpago() + ")", null);
    }

    /**
     * Entrada por el anticipo en efectivo de una orden de servicio. La caja es la indicada o la principal de
     * la tienda de la orden. Devuelve el id del movimiento, para poder devolver el anticipo si se cancela.
     */
    @Transactional
    public Integer registrarAnticipoServicio(Integer idCaja, OrdenServicio orden, OrdenServicioAnticipo anticipo, Usuario usuario) {
        Caja caja = cajaParaTienda(idCaja, orden.getTienda());
        validarAcceso(usuario, caja);
        return guardar(caja, usuario, null, motivoSistema(ANTICIPO_SERVICIO), anticipo.getMonto(),
                "Anticipo orden " + orden.getFolio() + " (anticipo #" + anticipo.getIdanticipo() + ")", null).getIdmovimiento();
    }

    /** La caja indicada (que debe ser de la tienda) o, si no se indica, la principal de la tienda. */
    public Caja cajaParaTienda(Integer idCaja, Tienda tienda) {
        Caja caja = idCaja != null ? buscarCaja(idCaja) : cajaPrincipalDeTienda(tienda);
        if (!caja.getTienda().getCodti().equals(tienda.getCodti())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La caja " + caja.getIdCaja() + " no pertenece a la tienda " + tienda.getCodti() + ".");
        }
        return caja;
    }

    /** Salida que devuelve al cliente un anticipo en efectivo, en la misma caja donde entró. */
    @Transactional
    public void registrarDevolucionAnticipo(OrdenServicio orden, OrdenServicioAnticipo anticipo, Usuario usuario) {
        MovimientoCaja original = movimientoCajaRepository.findById(anticipo.getIdmovimientoCaja())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se encontró el movimiento de caja del anticipo #" + anticipo.getIdanticipo() + "."));
        guardar(original.getCaja(), usuario, null, motivoSistema(DEVOLUCION_ANTICIPO), anticipo.getMonto(),
                "Devolución de anticipo orden " + orden.getFolio() + " (anticipo #" + anticipo.getIdanticipo() + ")",
                original.getIdmovimiento());
    }

    private Caja cajaPrincipalDe(Usuario usuario) {
        return cajaPrincipalDeTienda(usuario.getTienda());
    }

    private Caja cajaPrincipalDeTienda(Tienda tienda) {
        if (tienda != null) {
            List<Caja> cajas = cajaRepository.findByTienda_Codti(tienda.getCodti());
            Optional<Caja> principal = cajas.stream().filter(c -> Boolean.TRUE.equals(c.getEsCajaPrincipal())).findFirst();
            if (principal.isPresent()) return principal.get();
            if (cajas.size() == 1) return cajas.get(0);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Un pago en efectivo entra a una caja: indique el parámetro idCaja.");
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MovimientoCajaResponseDto> listar(Integer idCaja, LocalDateTime desde, LocalDateTime hasta, String username) {
        Caja caja = buscarCaja(idCaja);
        validarAcceso(usuarioPorUsername(username), caja);
        LocalDateTime desdeEfectivo = desde != null ? desde : LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime hastaEfectivo = hasta != null ? hasta : LocalDateTime.now();
        return movimientoCajaRepository
                .findByCaja_IdCajaAndFechaMovBetweenOrderByFechaMovDesc(idCaja, desdeEfectivo, hastaEfectivo)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SaldoCajaResponseDto saldo(Integer idCaja, String username) {
        Caja caja = buscarCaja(idCaja);
        validarAcceso(usuarioPorUsername(username), caja);
        BigDecimal entradas = movimientoCajaRepository.totalPorTipo(idCaja, ENTRADA);
        BigDecimal salidas  = movimientoCajaRepository.totalPorTipo(idCaja, SALIDA);
        return SaldoCajaResponseDto.builder()
                .idCaja(caja.getIdCaja())
                .nombreCaja(caja.getNombreCaja())
                .totalEntradas(entradas)
                .totalSalidas(salidas)
                .saldo(entradas.subtract(salidas))
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MovimientoCaja guardar(Caja caja, Usuario encargado, Usuario admin, CatMotivo motivo,
                                   BigDecimal monto, String observaciones, Integer idmovRef) {
        return movimientoCajaRepository.save(MovimientoCaja.builder()
                .caja(caja)
                .usuarioEncargado(encargado)
                .usuarioAdmin(admin)
                .motivo(motivo)
                .tipo(motivo.getTipoMov())   // copia del tipo del motivo (redundancia intencional del modelo)
                .monto(monto)
                .idmovRef(idmovRef)
                .observaciones(observaciones)
                .build());
    }

    private BigDecimal saldoDe(Integer idCaja) {
        return movimientoCajaRepository.totalPorTipo(idCaja, ENTRADA)
                .subtract(movimientoCajaRepository.totalPorTipo(idCaja, SALIDA));
    }

    private String referenciaVenta(Integer idventa) {
        return "Venta #" + idventa;
    }

    private CatMotivo motivoSistema(String nombre) {
        return catMotivoRepository.findByNombre(nombre)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Falta el motivo de caja '" + nombre + "'. Reinicie la aplicación para que se cree."));
    }

    private Caja buscarCaja(Integer idCaja) {
        return cajaRepository.findById(idCaja)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caja no encontrada: " + idCaja));
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private boolean esAdmin(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    /** ROOT/ADMIN operan cualquier caja; el resto solo las de su propia tienda. */
    private void validarAcceso(Usuario usuario, Caja caja) {
        if (esAdmin(usuario)) return;
        if (usuario.getTienda() == null || !usuario.getTienda().getCodti().equals(caja.getTienda().getCodti())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a las cajas de otra tienda.");
        }
    }

    private MovimientoCajaResponseDto toDto(MovimientoCaja m) {
        return MovimientoCajaResponseDto.builder()
                .idmovimiento(m.getIdmovimiento())
                .idCaja(m.getCaja().getIdCaja())
                .nombreCaja(m.getCaja().getNombreCaja())
                .idmotivo(m.getMotivo().getIdmotivo())
                .nombreMotivo(m.getMotivo().getNombre())
                .tipo(m.getTipo())
                .tipoDisplay(m.getTipo() != null && m.getTipo() == ENTRADA ? "Entrada" : "Salida")
                .monto(m.getMonto())
                .idusuarioEncargado(m.getUsuarioEncargado().getIdusuario())
                .nombreEncargado(m.getUsuarioEncargado().getNombreCompleto())
                .idusuarioAdmin(m.getUsuarioAdmin() != null ? m.getUsuarioAdmin().getIdusuario() : null)
                .idmovRef(m.getIdmovRef())
                .fechaMov(m.getFechaMov())
                .observaciones(m.getObservaciones())
                .build();
    }
}
