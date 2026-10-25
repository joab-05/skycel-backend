package com.skycel.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skycel.backend.domain.entity.Caja;
import com.skycel.backend.domain.entity.MovimientoCaja;
import com.skycel.backend.domain.entity.SesionCaja;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.entity.Venta;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.caja.AbrirCajaRequestDto;
import com.skycel.backend.dto.caja.CerrarCajaRequestDto;
import com.skycel.backend.dto.caja.SesionCajaResponseDto;
import com.skycel.backend.dto.caja.SesionCajaResponseDto.MetodoDto;
import com.skycel.backend.dto.caja.SesionCajaResponseDto.MotivoDto;
import com.skycel.backend.dto.caja.SesionCajaResponseDto.ResumenDto;
import com.skycel.backend.repository.CajaRepository;
import com.skycel.backend.repository.MovimientoCajaRepository;
import com.skycel.backend.repository.SesionCajaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.skycel.backend.service.MotivoCajaService.ABONO_CUENTA;
import static com.skycel.backend.service.MotivoCajaService.AJUSTE_APERTURA;
import static com.skycel.backend.service.MotivoCajaService.ENTRADA;
import static com.skycel.backend.service.MotivoCajaService.FONDO_APERTURA;
import static com.skycel.backend.service.MotivoCajaService.RETIRO_CIERRE;
import static com.skycel.backend.service.MotivoCajaService.SALIDA;

/**
 * Turnos de caja: abrir al iniciar el día y cerrar al terminar, con el corte guardado.
 *
 * <p>El efectivo esperado es el saldo de la caja (entradas menos salidas). Para que ese saldo y el cajón físico coincidan
 * de un día al otro, la apertura y el cierre dejan su propio movimiento:
 * <ul>
 *   <li>Al <b>abrir</b> con un fondo distinto al saldo, un movimiento "Fondo de apertura" (entrada) o "Ajuste de apertura"
 *       (salida) deja el saldo igual al fondo declarado. Así cualquier diferencia del día anterior se absorbe ahí.</li>
 *   <li>Al <b>cerrar</b>, "Retiro por cierre de caja" saca lo contado menos lo que se deja para mañana.</li>
 * </ul>
 * Esos tres movimientos no cuentan como entradas o salidas del turno en el corte.
 */
@Service
@RequiredArgsConstructor
public class SesionCajaService {

    private static final Set<String> MOTIVOS_DE_TURNO = Set.of(FONDO_APERTURA, AJUSTE_APERTURA, RETIRO_CIERRE);

    private final SesionCajaRepository     sesionCajaRepository;
    private final CajaRepository           cajaRepository;
    private final UsuarioRepository        usuarioRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final MovimientoCajaService    movimientoCajaService;
    private final VentaRepository          ventaRepository;
    private final ObjectMapper             objectMapper;

    // ── Consultas ────────────────────────────────────────────────────────────

    /** El turno abierto de la caja con su resumen en vivo o, si está cerrada, con cuánto se sugiere abrir. */
    @Transactional(readOnly = true)
    public SesionCajaResponseDto actual(Integer idCaja, String username) {
        Usuario usuario = usuario(username);
        Caja caja = caja(idCaja);
        validarAcceso(usuario, caja);
        return sesionCajaRepository.findFirstByCaja_IdCajaAndEstado(idCaja, SesionCaja.ABIERTA)
                .map(s -> toDtoAbierta(s))
                .orElseGet(() -> SesionCajaResponseDto.builder()
                        .idCaja(caja.getIdCaja()).nombreCaja(caja.getNombreCaja())
                        .codti(caja.getTienda().getCodti()).nombreTienda(caja.getTienda().getNombre())
                        .abierta(false).fondoSugerido(fondoSugerido(idCaja))
                        .build());
    }

    /** Historial de turnos de la caja (los más recientes primero), sin el detalle de cada uno. */
    @Transactional(readOnly = true)
    public List<SesionCajaResponseDto> historial(Integer idCaja, int limite, String username) {
        Caja caja = caja(idCaja);
        validarAcceso(usuario(username), caja);
        int tope = Math.max(1, Math.min(limite, 200));
        return sesionCajaRepository.findByCaja_IdCajaOrderByFechaAperturaDesc(idCaja, PageRequest.of(0, tope)).stream()
                .map(s -> s.getEstado() == SesionCaja.ABIERTA ? toDtoAbierta(s) : toDtoCerrada(s, false))
                .collect(Collectors.toList());
    }

    /** Un turno con su corte completo. */
    @Transactional(readOnly = true)
    public SesionCajaResponseDto obtener(Integer idsesion, String username) {
        SesionCaja s = sesionCajaRepository.findById(idsesion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno de caja no encontrado: " + idsesion));
        validarAcceso(usuario(username), s.getCaja());
        return s.getEstado() == SesionCaja.ABIERTA ? toDtoAbierta(s) : toDtoCerrada(s, true);
    }

    // ── Abrir ────────────────────────────────────────────────────────────────

    @Transactional
    public SesionCajaResponseDto abrir(Integer idCaja, AbrirCajaRequestDto dto, String username) {
        // Una apertura hecha sin conexión que ya se había enviado: se devuelve el mismo turno, no se abre otro
        if (dto.getClaveOffline() != null && !dto.getClaveOffline().isBlank()) {
            var existente = sesionCajaRepository.findFirstByClaveApertura(dto.getClaveOffline().trim());
            if (existente.isPresent()) {
                return existente.get().getEstado() == SesionCaja.ABIERTA ? toDtoAbierta(existente.get()) : toDtoCerrada(existente.get(), false);
            }
        }
        Usuario usuario = usuario(username);
        Caja caja = caja(idCaja);
        validarAcceso(usuario, caja);

        sesionCajaRepository.findFirstByCaja_IdCajaAndEstado(idCaja, SesionCaja.ABIERTA).ifPresent(abierta -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La caja ya está abierta desde "
                    + abierta.getFechaApertura().toLocalDate() + " " + abierta.getFechaApertura().toLocalTime().withNano(0)
                    + " por " + abierta.getUsuarioApertura().getNombreCompleto() + ".");
        });

        BigDecimal fondo = dto.getFondoInicial().setScale(2, java.math.RoundingMode.HALF_UP);
        boolean sinConexion = dto.getClaveOffline() != null && !dto.getClaveOffline().isBlank();
        LocalDateTime ahora = sinConexion ? MovimientoCajaService.fechaNoFutura(dto.getFecha()) : LocalDateTime.now();
        SesionCaja sesion = sesionCajaRepository.save(SesionCaja.builder()
                .caja(caja).usuarioApertura(usuario).fechaApertura(ahora)
                .claveApertura(sinConexion ? dto.getClaveOffline().trim() : null)
                .estado(SesionCaja.ABIERTA).fondoInicial(fondo).build());

        // Deja el saldo del sistema igual al efectivo con el que se abre
        BigDecimal saldo = saldoDe(idCaja);
        BigDecimal diferencia = fondo.subtract(saldo);
        if (diferencia.signum() > 0) {
            registrarDeTurno(caja, usuario, FONDO_APERTURA, diferencia,
                    "Apertura de caja · turno #" + sesion.getIdsesionCaja(), sinConexion ? ahora : null);
        } else if (diferencia.signum() < 0) {
            registrarDeTurno(caja, usuario, AJUSTE_APERTURA, diferencia.negate(),
                    "Apertura de caja · turno #" + sesion.getIdsesionCaja() + " (el sistema esperaba $" + saldo + ")", sinConexion ? ahora : null);
        }
        return toDtoAbierta(sesion);
    }

    // ── Cerrar ───────────────────────────────────────────────────────────────

    @Transactional
    public SesionCajaResponseDto cerrar(Integer idCaja, CerrarCajaRequestDto dto, String username) {
        // Un cierre hecho sin conexión que ya se había enviado: se devuelve el mismo corte
        if (dto.getClaveOffline() != null && !dto.getClaveOffline().isBlank()) {
            var existente = sesionCajaRepository.findFirstByClaveCierre(dto.getClaveOffline().trim());
            if (existente.isPresent()) return toDtoCerrada(existente.get(), true);
        }
        Usuario usuario = usuario(username);
        Caja caja = caja(idCaja);
        validarAcceso(usuario, caja);

        SesionCaja sesion = sesionCajaRepository.findFirstByCaja_IdCajaAndEstado(idCaja, SesionCaja.ABIERTA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La caja no está abierta: no hay turno que cerrar."));

        BigDecimal contado = dto.getEfectivoContado().setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal fondoSiguiente = dto.getFondoSiguiente() != null
                ? dto.getFondoSiguiente().setScale(2, java.math.RoundingMode.HALF_UP)
                : sesion.getFondoInicial().min(contado);
        if (fondoSiguiente.compareTo(contado) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El fondo que se deja para mañana ($" + fondoSiguiente + ") no puede ser mayor a lo contado ($" + contado + ").");
        }

        boolean sinConexion = dto.getClaveOffline() != null && !dto.getClaveOffline().isBlank();
        LocalDateTime ahora = sinConexion ? MovimientoCajaService.fechaNoFutura(dto.getFecha()) : LocalDateTime.now();
        BigDecimal esperado = saldoDe(idCaja);
        BigDecimal retiro = contado.subtract(fondoSiguiente);
        ResumenDto resumen = resumen(idCaja, sesion.getFechaApertura(), ahora);

        sesion.setUsuarioCierre(usuario);
        sesion.setClaveCierre(sinConexion ? dto.getClaveOffline().trim() : null);
        sesion.setFechaCierre(ahora);
        sesion.setEstado(SesionCaja.CERRADA);
        sesion.setEfectivoEsperado(esperado);
        sesion.setEfectivoContado(contado);
        sesion.setDiferencia(contado.subtract(esperado));
        sesion.setFondoSiguiente(fondoSiguiente);
        sesion.setRetiroCierre(retiro);
        sesion.setObservaciones(dto.getObservaciones() != null && !dto.getObservaciones().isBlank() ? dto.getObservaciones().trim() : null);
        sesion.setDetalle(aJson(resumen));
        sesionCajaRepository.save(sesion);

        if (retiro.signum() > 0) {
            registrarDeTurno(caja, usuario, RETIRO_CIERRE, retiro,
                    "Cierre de caja · turno #" + sesion.getIdsesionCaja(), sinConexion ? ahora : null);
        }
        return toDtoCerrada(sesion, true);
    }

    // ── Piezas internas ──────────────────────────────────────────────────────

    /** Lo que ocurrió en la caja entre {@code desde} y {@code hasta}: ventas por método y movimientos por motivo. */
    ResumenDto resumen(Integer idCaja, LocalDateTime desde, LocalDateTime hasta) {
        List<Venta> ventas = ventaRepository.findByCaja_IdCajaAndFechaVentaBetween(idCaja, desde, hasta);
        List<Venta> completadas = ventas.stream().filter(v -> v.getEstado() != null && v.getEstado() == 1).collect(Collectors.toList());
        Map<String, MetodoDto> porMetodo = new LinkedHashMap<>();
        for (Venta v : completadas) {
            MetodoDto m = porMetodo.computeIfAbsent(nombreMetodo(v.getMetodoPago()),
                    k -> MetodoDto.builder().metodo(k).cantidad(0).total(BigDecimal.ZERO).build());
            m.setCantidad(m.getCantidad() + 1);
            m.setTotal(m.getTotal().add(v.getTotal() != null ? v.getTotal() : BigDecimal.ZERO));
        }

        List<MovimientoCaja> movimientos = movimientoCajaRepository
                .findByCaja_IdCajaAndFechaMovBetweenOrderByFechaMovDesc(idCaja, desde, hasta).stream()
                .filter(m -> !MOTIVOS_DE_TURNO.contains(m.getMotivo().getNombre()))
                .collect(Collectors.toList());
        Map<String, MotivoDto> porMotivo = new LinkedHashMap<>();
        BigDecimal entradas = BigDecimal.ZERO, salidas = BigDecimal.ZERO, abonos = BigDecimal.ZERO;
        int numAbonos = 0;
        for (MovimientoCaja m : movimientos) {
            int tipo = m.getTipo() != null && m.getTipo() == ENTRADA ? 1 : 2;
            MotivoDto d = porMotivo.computeIfAbsent(tipo + "|" + m.getMotivo().getNombre(),
                    k -> MotivoDto.builder().motivo(m.getMotivo().getNombre()).tipo(tipo).cantidad(0).total(BigDecimal.ZERO).build());
            d.setCantidad(d.getCantidad() + 1);
            d.setTotal(d.getTotal().add(m.getMonto()));
            if (tipo == 1) entradas = entradas.add(m.getMonto()); else salidas = salidas.add(m.getMonto());
            if (ABONO_CUENTA.equals(m.getMotivo().getNombre())) { abonos = abonos.add(m.getMonto()); numAbonos++; }
        }
        List<MotivoDto> detalle = new ArrayList<>(porMotivo.values());
        detalle.sort(Comparator.comparingInt(MotivoDto::getTipo).thenComparing(Comparator.comparing(MotivoDto::getTotal).reversed()));

        return ResumenDto.builder()
                .numVentas(completadas.size())
                .totalVentas(completadas.stream().map(v -> v.getTotal() != null ? v.getTotal() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add))
                .ventasCanceladas(ventas.size() - completadas.size())
                .ventasPorMetodo(new ArrayList<>(porMetodo.values()))
                .numAbonos(numAbonos).totalAbonos(abonos)
                .totalEntradas(entradas).totalSalidas(salidas)
                .movimientos(detalle)
                .build();
    }

    private SesionCajaResponseDto toDtoAbierta(SesionCaja s) {
        SesionCajaResponseDto dto = base(s);
        dto.setAbierta(true);
        dto.setEfectivoEsperado(saldoDe(s.getCaja().getIdCaja()));
        dto.setResumen(resumen(s.getCaja().getIdCaja(), s.getFechaApertura(), LocalDateTime.now()));
        return dto;
    }

    private SesionCajaResponseDto toDtoCerrada(SesionCaja s, boolean conDetalle) {
        SesionCajaResponseDto dto = base(s);
        dto.setAbierta(false);
        dto.setUsuarioCierre(s.getUsuarioCierre() != null ? s.getUsuarioCierre().getNombreCompleto() : null);
        dto.setFechaCierre(s.getFechaCierre());
        dto.setEfectivoEsperado(s.getEfectivoEsperado());
        dto.setEfectivoContado(s.getEfectivoContado());
        dto.setDiferencia(s.getDiferencia());
        dto.setFondoSiguiente(s.getFondoSiguiente());
        dto.setRetiroCierre(s.getRetiroCierre());
        dto.setObservaciones(s.getObservaciones());
        if (conDetalle) {
            ResumenDto guardado = deJson(s.getDetalle());
            dto.setResumen(guardado);
            // ¿Entró algo con fecha de este turno después de cerrarlo? (p. ej. ventas hechas sin conexión que llegaron tarde)
            if (guardado != null && s.getFechaCierre() != null) {
                ResumenDto ahora = resumen(s.getCaja().getIdCaja(), s.getFechaApertura(), s.getFechaCierre());
                dto.setModificadaDespuesDelCierre(
                        ahora.getTotalEntradas().compareTo(guardado.getTotalEntradas()) != 0
                                || ahora.getTotalSalidas().compareTo(guardado.getTotalSalidas()) != 0
                                || ahora.getNumVentas() != guardado.getNumVentas());
            }
        }
        return dto;
    }

    private SesionCajaResponseDto base(SesionCaja s) {
        return SesionCajaResponseDto.builder()
                .idsesionCaja(s.getIdsesionCaja())
                .idCaja(s.getCaja().getIdCaja()).nombreCaja(s.getCaja().getNombreCaja())
                .codti(s.getCaja().getTienda().getCodti()).nombreTienda(s.getCaja().getTienda().getNombre())
                .usuarioApertura(s.getUsuarioApertura().getNombreCompleto())
                .fechaApertura(s.getFechaApertura())
                .fondoInicial(s.getFondoInicial())
                .build();
    }

    /** Con cuánto se sugiere abrir: lo que se dejó en el cajón al cerrar el turno anterior; sin turnos previos, el saldo de la caja. */
    private BigDecimal fondoSugerido(Integer idCaja) {
        return sesionCajaRepository.findFirstByCaja_IdCajaAndEstadoOrderByFechaCierreDesc(idCaja, SesionCaja.CERRADA)
                .map(SesionCaja::getFondoSiguiente)
                .orElseGet(() -> saldoDe(idCaja).max(BigDecimal.ZERO));
    }

    /** El movimiento del turno; con fecha solo si el turno se abrió o cerró sin conexión. */
    private void registrarDeTurno(Caja caja, Usuario usuario, String motivo, BigDecimal monto, String observaciones, LocalDateTime fecha) {
        if (fecha == null) movimientoCajaService.registrarDeTurno(caja, usuario, motivo, monto, observaciones);
        else movimientoCajaService.registrarDeTurno(caja, usuario, motivo, monto, observaciones, fecha);
    }

    private BigDecimal saldoDe(Integer idCaja) {
        return movimientoCajaRepository.totalPorTipo(idCaja, ENTRADA).subtract(movimientoCajaRepository.totalPorTipo(idCaja, SALIDA));
    }

    private static String nombreMetodo(Byte metodo) {
        if (metodo == null) return "Efectivo";
        return switch (metodo) {
            case 2 -> "Tarjeta";
            case 3 -> "Transferencia";
            case 4 -> "Mixto";
            case 5 -> "PayJoy";
            case 6 -> "Anticipo";
            case 7 -> "Crédito por devolución";
            default -> "Efectivo";
        };
    }

    private String aJson(ResumenDto r) {
        try {
            return objectMapper.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el detalle del corte.");
        }
    }

    private ResumenDto deJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ResumenDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Caja caja(Integer idCaja) {
        return cajaRepository.findById(idCaja)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caja no encontrada: " + idCaja));
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    /** ROOT/ADMIN cualquier caja; un vendedor, la de la sucursal donde trabaja ese día (rotan); el resto, solo la de su tienda. */
    private void validarAcceso(Usuario usuario, Caja caja) {
        if (usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN || usuario.getRol() == Rol.VENDEDOR) return;
        if (usuario.getTienda() == null || !usuario.getTienda().getCodti().equals(caja.getTienda().getCodti())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a las cajas de otra tienda.");
        }
    }
}
