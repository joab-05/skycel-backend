package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.dto.equipo.EquipoResponseDto;
import com.skycel.backend.dto.equipo.RastroEquipoDto;
import com.skycel.backend.dto.equipo.RastroEquipoDto.EventoDto;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El rastreo de un equipo: desde que entra al sistema hasta donde está hoy. Junta, en orden, los movimientos de su historial
 * (alta, traspasos, venta, devolución, baja, ajustes) y le agrega lo que cada uno quiere decir: a quién se vendió, de qué
 * sucursal a cuál viajó, y sus reclamos de garantía.
 */
@Service
@RequiredArgsConstructor
public class RastroEquipoService {

    private static final Pattern NUMERO = Pattern.compile("#\\s*(\\d+)");

    private final EquipoService equipoService;
    private final MovimientoInventarioRepository movimientoRepository;
    private final TiendaRepository tiendaRepository;
    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final TraspasoDetalleRepository traspasoDetalleRepository;
    private final GarantiaRepository garantiaRepository;

    @Transactional(readOnly = true)
    public RastroEquipoDto rastro(String imei, String username) {
        String id = imei == null ? "" : imei.trim().toUpperCase();
        EquipoResponseDto equipo = equipoService.porImei(id, username);   // 404 si no existe

        Map<Integer, String> tiendas = new HashMap<>();
        for (Tienda t : tiendaRepository.findAll()) tiendas.put(t.getCodti(), t.getNombre());

        // Los traspasos en que viajó, por su folio (el número que va en la referencia del movimiento)
        Map<Integer, Traspaso> traspasos = new HashMap<>();
        for (TraspasoDetalle d : traspasoDetalleRepository.findByImei(id)) {
            Traspaso t = d.getTraspaso();
            if (t != null) traspasos.put(t.getIdtraspasoRef() != null ? t.getIdtraspasoRef() : t.getIdtraspaso(), t);
        }
        // Las ventas en que salió, por su número
        Map<Integer, VentaDetalle> ventas = new HashMap<>();
        for (VentaDetalle d : ventaDetalleRepository.findByImei(id)) {
            if (d.getVenta() != null) ventas.put(d.getVenta().getIdventa(), d);
        }

        List<EventoDto> eventos = new ArrayList<>();
        boolean conAlta = false;
        int numTraspasos = 0;
        LocalDateTime fechaVenta = null;
        for (MovimientoInventario m : movimientoRepository.rastro(id)) {
            if ("ALTA".equals(m.getTipo())) conAlta = true;
            if ("TRASPASO_SALIDA".equals(m.getTipo())) numTraspasos++;
            if ("VENTA".equals(m.getTipo())) fechaVenta = m.getFecha();
            if ("CANCELACION_VENTA".equals(m.getTipo()) || "DEVOLUCION".equals(m.getTipo())) fechaVenta = null;
            eventos.add(deMovimiento(m, tiendas, traspasos, ventas));
        }
        if (!conAlta && equipo.getFechaRegistro() != null) {
            eventos.add(0, EventoDto.builder().fecha(equipo.getFechaRegistro()).tipo("ALTA").titulo("Se registró en el sistema")
                    .detalle(equipo.getNombreProveedor() != null ? "Proveedor: " + equipo.getNombreProveedor() : null).build());
        }

        List<Garantia> garantias = garantiaRepository.findByImei(id);
        for (Garantia g : garantias) {
            eventos.add(EventoDto.builder().fecha(g.getFechaIngreso()).tipo("GARANTIA")
                    .titulo("Reclamo de garantía " + g.getFolioSeguimiento())
                    .detalle("Falla: " + g.getFallaReportada() + " · hoy: " + estadoGarantia(g.getEstadoActual())
                            + (g.getProveedor() != null ? " · proveedor: " + g.getProveedor().getNombreFiscal() : ""))
                    .referencia(g.getFolioSeguimiento())
                    .codti(g.getTienda() != null ? g.getTienda().getCodti() : null)
                    .tienda(g.getTienda() != null ? g.getTienda().getNombre() : null).build());
        }
        eventos.sort(Comparator.comparing(EventoDto::getFecha, Comparator.nullsLast(Comparator.naturalOrder())));

        // El recorrido: las sucursales en orden, sin repetir la misma dos veces seguidas
        List<String> recorrido = new ArrayList<>();
        for (EventoDto e : eventos) {
            if (e.getTienda() != null && (recorrido.isEmpty() || !recorrido.get(recorrido.size() - 1).equals(e.getTienda()))) {
                recorrido.add(e.getTienda());
            }
        }

        LocalDateTime ingreso = eventos.isEmpty() ? equipo.getFechaRegistro() : eventos.get(0).getFecha();
        LocalDateTime hasta = fechaVenta != null ? fechaVenta : LocalDateTime.now();
        Long dias = ingreso == null ? null : Math.max(0, ChronoUnit.DAYS.between(ingreso, hasta));

        return RastroEquipoDto.builder().equipo(equipo).eventos(eventos).ingreso(ingreso).diasEnInventario(dias)
                .recorrido(recorrido).traspasos(numTraspasos).garantias(garantias.size())
                .vendido(fechaVenta != null).build();
    }

    private EventoDto deMovimiento(MovimientoInventario m, Map<Integer, String> tiendas, Map<Integer, Traspaso> traspasos, Map<Integer, VentaDetalle> ventas) {
        String tienda = m.getCodti() != null ? tiendas.get(m.getCodti()) : null;
        String donde = tienda != null ? tienda : "la sucursal";
        String ref = m.getReferencia();
        Integer numero = numeroDe(ref);
        String titulo;
        String detalle = m.getMotivo();
        BigDecimal monto = null;
        switch (m.getTipo()) {
            case "ALTA" -> titulo = "Ingresó al inventario de " + donde;
            case "ENTRADA" -> titulo = "Entró a " + donde;
            case "TRASPASO_SALIDA" -> {
                Traspaso t = numero == null ? null : traspasos.get(numero);
                titulo = "Salió de " + donde + " (traspaso)";
                if (t != null) detalle = "Va a " + t.getTiendaDestino().getNombre();
            }
            case "TRASPASO_ENTRADA" -> {
                Traspaso t = numero == null ? null : traspasos.get(numero);
                titulo = "Llegó a " + donde + " (traspaso)";
                if (t != null) detalle = "Viene de " + t.getTiendaOrigen().getNombre();
            }
            case "TRASPASO_ANULADO" -> titulo = "Se anuló el traspaso: regresó a " + donde;
            case "TRASPASO_FALTANTE" -> titulo = "Traspaso: " + (m.getCantidad().signum() > 0 ? "llegó o regresó a " + donde + " lo que faltaba" : "faltante");
            case "VENTA" -> {
                VentaDetalle d = numero == null ? null : ventas.get(numero);
                titulo = "Vendido en " + donde;
                if (d != null && d.getVenta() != null) {
                    Venta v = d.getVenta();
                    monto = d.getPrecioUnitarioFinal();
                    detalle = "Cliente: " + (v.getCliente() != null ? v.getCliente().getNombreCompleto() : "mostrador")
                            + (v.getUsuarioVendedor() != null ? " · atendió: " + v.getUsuarioVendedor().getNombreCompleto() : "")
                            + (m.getMotivo() != null ? " · " + m.getMotivo() : "");
                }
            }
            case "CANCELACION_VENTA" -> titulo = "Se canceló la venta: regresó a " + donde;
            case "DEVOLUCION" -> titulo = "Lo devolvió el cliente a " + donde;
            case "GARANTIA_VITRINA" -> titulo = m.getCantidad().signum() < 0 ? "Salió por garantía de vitrina" : "Regresó de garantía de vitrina";
            case "SALIDA" -> titulo = "Dado de baja en " + donde;
            case "AJUSTE" -> titulo = m.getCantidad().signum() < 0 ? "Ajuste: salió de " + donde : "Ajuste: volvió a estar disponible en " + donde;
            default -> titulo = m.getTipo();
        }
        return EventoDto.builder().fecha(m.getFecha()).tipo(m.getTipo()).titulo(titulo).detalle(detalle)
                .codti(m.getCodti()).tienda(tienda)
                .usuario(m.getUsuario() != null ? m.getUsuario().getNombreCompleto() : null)
                .referencia(ref).monto(monto).build();
    }

    private static Integer numeroDe(String referencia) {
        if (referencia == null) return null;
        Matcher m = NUMERO.matcher(referencia);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static String estadoGarantia(Byte estado) {
        if (estado == null) return "—";
        return switch (estado) {
            case 1 -> "recibido en sucursal";
            case 2 -> "en tránsito a bodega";
            case 3 -> "recibido en bodega";
            case 4 -> "en tránsito al proveedor";
            case 5 -> "en el proveedor";
            case 6 -> "reparado por el proveedor";
            case 7 -> "cambio físico";
            case 8 -> "rechazado";
            case 9 -> "viaje de retorno";
            case 10 -> "listo para entrega";
            case 11 -> "entregado";
            default -> "desconocido";
        };
    }
}
