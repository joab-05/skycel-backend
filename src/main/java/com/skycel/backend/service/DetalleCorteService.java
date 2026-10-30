package com.skycel.backend.service;

import com.skycel.backend.domain.entity.MovimientoCaja;
import com.skycel.backend.domain.entity.SesionCaja;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.entity.Venta;
import com.skycel.backend.domain.entity.VentaDetalle;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.caja.DetalleCorteDto;
import com.skycel.backend.dto.caja.DetalleCorteDto.ArticuloDto;
import com.skycel.backend.dto.caja.DetalleCorteDto.EquiposDto;
import com.skycel.backend.dto.caja.DetalleCorteDto.MovimientoDto;
import com.skycel.backend.dto.caja.DetalleCorteDto.PagoDto;
import com.skycel.backend.dto.caja.DetalleCorteDto.VentaDto;
import com.skycel.backend.repository.MovimientoCajaRepository;
import com.skycel.backend.repository.SesionCajaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.repository.VentaDetalleRepository;
import com.skycel.backend.repository.VentaPagoDetalleRepository;
import com.skycel.backend.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static com.skycel.backend.service.MotivoCajaService.ABONO_CUENTA;
import static com.skycel.backend.service.MotivoCajaService.AJUSTE_APERTURA;
import static com.skycel.backend.service.MotivoCajaService.ENTRADA;
import static com.skycel.backend.service.MotivoCajaService.FONDO_APERTURA;
import static com.skycel.backend.service.MotivoCajaService.RETIRO_CIERRE;
import static com.skycel.backend.service.MotivoCajaService.VENTA;

/**
 * El detalle de un turno de caja, para que un encargado o administrador confirme que todo lo vendido y todo el efectivo que entró o
 * salió quedó registrado: ventas con sus artículos (equipos por IMEI, contado o PayJoy) y movimientos con su motivo y nota.
 */
@Service
@RequiredArgsConstructor
public class DetalleCorteService {

    private static final java.util.regex.Pattern REFERENCIA_VENTA = java.util.regex.Pattern.compile("Venta #(\\d+)");
    private static final Set<String> MOTIVOS_DE_TURNO = Set.of(FONDO_APERTURA, AJUSTE_APERTURA, RETIRO_CIERRE);

    private final SesionCajaRepository sesionCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final VentaPagoDetalleRepository ventaPagoDetalleRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;

    @Transactional(readOnly = true)
    public DetalleCorteDto detalle(Integer idsesion, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        SesionCaja s = sesionCajaRepository.findById(idsesion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno no encontrado: " + idsesion));
        validarAcceso(usuario, s);

        boolean abierta = s.getEstado() == SesionCaja.ABIERTA;
        LocalDateTime desde = s.getFechaApertura();
        LocalDateTime hasta = abierta || s.getFechaCierre() == null ? LocalDateTime.now() : s.getFechaCierre();
        Integer idCaja = s.getCaja().getIdCaja();

        // el efectivo que cada venta dejó en el cajón: las entradas «Venta #n» del turno
        List<MovimientoCaja> delTurno = new ArrayList<>(
                movimientoCajaRepository.findByCaja_IdCajaAndFechaMovBetweenOrderByFechaMovDesc(idCaja, desde, hasta));
        delTurno.sort(Comparator.comparing(MovimientoCaja::getFechaMov));
        Map<Integer, BigDecimal> efectivoPorVenta = new HashMap<>();
        for (MovimientoCaja m : delTurno) {
            if (!VENTA.equals(m.getMotivo().getNombre()) || m.getObservaciones() == null) continue;
            java.util.regex.Matcher mt = REFERENCIA_VENTA.matcher(m.getObservaciones());
            if (!mt.matches()) continue;
            BigDecimal con = m.getTipo() != null && m.getTipo() == ENTRADA ? m.getMonto() : m.getMonto().negate();
            efectivoPorVenta.merge(Integer.valueOf(mt.group(1)), con, BigDecimal::add);
        }
        int conAlerta = 0;

        List<VentaDto> ventas = new ArrayList<>();
        int contado = 0, payjoy = 0;
        BigDecimal importeContado = BigDecimal.ZERO, enganches = BigDecimal.ZERO;
        List<Venta> deLaCaja = new ArrayList<>(ventaRepository.findByCaja_IdCajaAndFechaVentaBetween(idCaja, desde, hasta));
        deLaCaja.sort(Comparator.comparing(Venta::getFechaVenta));
        for (Venta v : deLaCaja) {
            boolean completada = v.getEstado() != null && v.getEstado() == 1;
            List<ArticuloDto> articulos = new ArrayList<>();
            for (VentaDetalle d : ventaDetalleRepository.findByVenta_Idventa(v.getIdventa())) {
                boolean equipo = d.getImei() != null && !d.getImei().isBlank();
                boolean esPayjoy = equipo && d.getEnganchePayjoy() != null;
                int cantidad = d.getCantidad() == null ? 1 : d.getCantidad();
                BigDecimal unitario = d.getPrecioUnitarioFinal() == null ? BigDecimal.ZERO : d.getPrecioUnitarioFinal();
                BigDecimal importe = unitario.multiply(BigDecimal.valueOf(cantidad));
                articulos.add(ArticuloDto.builder()
                        .codigo(d.getCodpro())
                        .nombre(d.getProductoMaster() != null ? d.getProductoMaster().getNombreBase() : d.getCodpro())
                        .cantidad(cantidad).precioUnitario(unitario).importe(importe)
                        .imei(equipo ? d.getImei() : null).equipo(equipo).payjoy(esPayjoy)
                        .enganche(esPayjoy ? d.getEnganchePayjoy() : null)
                        .plazoMeses(esPayjoy ? d.getPlazoMesesPayjoy() : null)
                        .regalo(Boolean.TRUE.equals(d.getEsRegalo()))
                        .promocion(d.getPromoTipo())
                        .precioAutorizado(Boolean.TRUE.equals(d.getPrecioAutorizado()))
                        .build());
                if (completada && equipo) {
                    if (esPayjoy) {
                        payjoy++;
                        enganches = enganches.add(d.getEnganchePayjoy());
                    } else {
                        contado++;
                        importeContado = importeContado.add(importe);
                    }
                }
            }
            List<PagoDto> pagos = new ArrayList<>();
            ventaPagoDetalleRepository.findByVenta_IdventaOrderByIdpagoDetalle(v.getIdventa()).forEach(p ->
                    pagos.add(PagoDto.builder().metodo(nombreMetodo(p.getMetodoPago())).monto(p.getMonto()).folio(p.getFolioOperacion()).build()));
            BigDecimal enCaja = efectivoPorVenta.getOrDefault(v.getIdventa(), BigDecimal.ZERO);
            String alerta = completada ? alertaDeEfectivo(v, articulos, pagos, enCaja) : null;
            if (alerta != null) conAlerta++;
            ventas.add(VentaDto.builder()
                    .idventa(v.getIdventa()).fecha(v.getFechaVenta())
                    .vendedor(v.getUsuarioVendedor() != null ? v.getUsuarioVendedor().getNombreCompleto() : null)
                    .cliente(v.getCliente() != null ? v.getCliente().getNombreCompleto() : null)
                    .metodo(nombreMetodo(v.getMetodoPago()))
                    .total(v.getTotal() == null ? BigDecimal.ZERO : v.getTotal())
                    .completada(completada)
                    .sinConexion(v.getClaveOffline() != null && !v.getClaveOffline().isBlank())
                    .observaciones(v.getObservaciones())
                    .articulos(articulos).pagos(pagos)
                    .efectivoEnCaja(enCaja).alerta(alerta)
                    .build());
        }

        List<MovimientoDto> movimientos = new ArrayList<>();
        for (MovimientoCaja m : delTurno) {
            String motivo = m.getMotivo().getNombre();
            String origen = MOTIVOS_DE_TURNO.contains(motivo) ? "TURNO" : VENTA.equals(motivo) ? "VENTA" : ABONO_CUENTA.equals(motivo) ? "ABONO" : "OTRO";
            movimientos.add(MovimientoDto.builder()
                    .idmovimiento(m.getIdmovimiento()).fecha(m.getFechaMov())
                    .tipo(m.getTipo() != null && m.getTipo() == ENTRADA ? 1 : 2)
                    .motivo(motivo).origen(origen).monto(m.getMonto())
                    .usuario(m.getUsuarioEncargado() != null ? m.getUsuarioEncargado().getNombreCompleto() : null)
                    .observaciones(m.getObservaciones())
                    .build());
        }

        return DetalleCorteDto.builder()
                .idsesionCaja(s.getIdsesionCaja()).idCaja(idCaja).nombreCaja(s.getCaja().getNombreCaja())
                .codti(s.getCaja().getTienda().getCodti()).nombreTienda(s.getCaja().getTienda().getNombre())
                .abierta(abierta).desde(desde).hasta(hasta)
                .ventas(ventas).movimientos(movimientos).ventasConAlerta(conAlerta)
                .equipos(EquiposDto.builder().contado(contado).payjoy(payjoy).importeContado(importeContado).enganchesPayjoy(enganches).build())
                .build();
    }

    /**
     * Compara el efectivo que la venta debió dejar en el cajón con el que quedó registrado. Devuelve null si cuadra o si no se puede
     * saber (un equipo con PayJoy: lo que entra depende del enganche y de cómo se cobró).
     */
    private static String alertaDeEfectivo(Venta v, List<ArticuloDto> articulos, List<PagoDto> pagos, BigDecimal enCaja) {
        if (articulos.stream().anyMatch(ArticuloDto::isPayjoy)) return null;
        String metodo = nombreMetodo(v.getMetodoPago());
        BigDecimal total = v.getTotal() == null ? BigDecimal.ZERO : v.getTotal();
        BigDecimal esperado;
        switch (metodo) {
            case "Efectivo" -> esperado = total;
            case "Mixto" -> esperado = pagos.stream().filter(p -> "Efectivo".equals(p.getMetodo())).map(PagoDto::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            case "Transferencia", "Tarjeta" -> esperado = BigDecimal.ZERO;
            default -> { return null; }   // anticipo, crédito por devolución...: no pasan por el cajón de la misma forma
        }
        if (enCaja.compareTo(esperado) == 0) return null;
        if (enCaja.signum() == 0) return "Debió dejar " + dinero(esperado) + " en efectivo y no hay entrada en la caja";
        return "Debió dejar " + dinero(esperado) + " en efectivo y la caja registró " + dinero(enCaja);
    }

    private static String dinero(BigDecimal d) {
        return "$" + d.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** ROOT y ADMIN, cualquier turno; un encargado, solo los de su tienda (el vendedor no llega aquí: lo frena el permiso). */
    private void validarAcceso(Usuario usuario, SesionCaja s) {
        if (usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN) return;
        if (usuario.getTienda() == null || !usuario.getTienda().getCodti().equals(s.getCaja().getTienda().getCodti())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a los cortes de otra tienda.");
        }
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
}
