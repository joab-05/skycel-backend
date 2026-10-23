package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.repository.DevolucionRepository;
import com.skycel.backend.repository.MovimientoCajaRepository;
import com.skycel.backend.repository.ProductoRepository;
import com.skycel.backend.repository.VentaDetalleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.skycel.backend.service.MotivoCajaService.*;

/**
 * Reportes para el dueño: estado de resultados (lo vendido, lo que costó, los gastos de caja y lo que queda,
 * comparado con el periodo anterior de la misma duración), ventas por día y por categoría, y los accesorios
 * que no se mueven. Mismo alcance que {@link ReporteService}: un encargado solo ve su sucursal.
 */
@Service
@RequiredArgsConstructor
public class ReporteResultadosService {

    private static final int DIAS_SIN_MOVIMIENTO_DEFAULT = 30;
    private static final int LIMITE_SIN_MOVIMIENTO = 300;

    /** Salidas de caja que mueven dinero pero no son un gasto del negocio (se muestran aparte o no se muestran). */
    private static final Set<String> NO_ES_GASTO = Set.of(CANCELACION_VENTA, DEVOLUCION_ANTICIPO, DEVOLUCION_VENTA,
            PAGO_PROVEEDOR, PRESTAMO_EMPLEADO, FONDO_APERTURA, AJUSTE_APERTURA, RETIRO_CIERRE);

    private final ReporteService reporteService;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final DevolucionRepository devolucionRepository;
    private final ProductoRepository productoRepository;

    // ── Estado de resultados ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> estadoResultados(LocalDate desde, LocalDate hasta, Integer codti, String username) {
        ReporteService.Alcance alc = reporteService.alcance(codti, username);
        LocalDate fHasta = hasta != null ? hasta : LocalDate.now();
        LocalDate fDesde = desde != null ? desde : fHasta.withDayOfMonth(1);
        validarRango(fDesde, fHasta);

        long dias = ChronoUnit.DAYS.between(fDesde, fHasta) + 1;
        LocalDate antHasta = fDesde.minusDays(1);
        LocalDate antDesde = antHasta.minusDays(dias - 1);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", fDesde);
        r.put("hasta", fHasta);
        r.put("anteriorDesde", antDesde);
        r.put("anteriorHasta", antHasta);
        r.put("actual", resultadosPeriodo(fDesde, fHasta, alc.codtis()));
        r.put("anterior", resultadosPeriodo(antDesde, antHasta, alc.codtis()));
        return r;
    }

    private Map<String, Object> resultadosPeriodo(LocalDate desde, LocalDate hasta, List<Integer> codtis) {
        LocalDateTime d = desde.atStartOfDay();
        LocalDateTime h = hasta.atTime(LocalTime.of(23, 59, 59));

        long numVentas = 0;
        BigDecimal ventas = BigDecimal.ZERO;
        BigDecimal costo = BigDecimal.ZERO;
        for (var fila : ventaDetalleRepository.resumenPorTienda(d, h, codtis)) {
            numVentas += fila.getNumVentas();
            ventas = ventas.add(fila.getTotalVenta());
            costo = costo.add(fila.getTotalCosto());
        }
        BigDecimal devoluciones = cero(devolucionRepository.totalDevuelto(d, h, codtis));
        BigDecimal costoReingresado = cero(devolucionRepository.costoReingresado(d, h, codtis));
        BigDecimal ventasNetas = ventas.subtract(devoluciones);
        BigDecimal costoNeto = costo.subtract(costoReingresado);
        BigDecimal utilidadBruta = ventasNetas.subtract(costoNeto);

        // Salidas de caja: gasto del negocio, o dinero que se movió sin ser gasto
        Map<String, BigDecimal> gastos = new TreeMap<>();
        BigDecimal retiros = BigDecimal.ZERO, pagosProveedor = BigDecimal.ZERO, prestamos = BigDecimal.ZERO;
        for (Object[] f : movimientoCajaRepository.totalesPorMotivo((byte) 2, d, h, codtis)) {
            String motivo = (String) f[0];
            Number catSat = (Number) f[1];
            BigDecimal monto = (BigDecimal) f[2];
            if (PAGO_PROVEEDOR.equals(motivo)) pagosProveedor = pagosProveedor.add(monto);
            else if (PRESTAMO_EMPLEADO.equals(motivo)) prestamos = prestamos.add(monto);
            else if (NO_ES_GASTO.contains(motivo)) continue;
            else if (catSat != null && (catSat.byteValue() == CAT_TRASLADO || catSat.byteValue() == CAT_PERSONAL)) retiros = retiros.add(monto);
            else gastos.merge(motivo, monto, BigDecimal::add);
        }
        BigDecimal abonos = BigDecimal.ZERO;
        for (Object[] f : movimientoCajaRepository.totalesPorMotivo((byte) 1, d, h, codtis)) {
            if (ABONO_CUENTA.equals(f[0])) abonos = abonos.add((BigDecimal) f[2]);
        }

        List<Map<String, Object>> listaGastos = new ArrayList<>();
        gastos.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("concepto", e.getKey());
                    g.put("monto", dinero(e.getValue()));
                    listaGastos.add(g);
                });
        BigDecimal totalGastos = gastos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal utilidadNeta = utilidadBruta.subtract(totalGastos);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numVentas", numVentas);
        m.put("ventas", dinero(ventas));
        m.put("devoluciones", dinero(devoluciones));
        m.put("ventasNetas", dinero(ventasNetas));
        m.put("costo", dinero(costoNeto));
        m.put("utilidadBruta", dinero(utilidadBruta));
        m.put("margenBruto", margen(utilidadBruta, ventasNetas));
        m.put("gastos", listaGastos);
        m.put("totalGastos", dinero(totalGastos));
        m.put("utilidadNeta", dinero(utilidadNeta));
        m.put("margenNeto", margen(utilidadNeta, ventasNetas));
        m.put("retiros", dinero(retiros));
        m.put("pagosProveedor", dinero(pagosProveedor));
        m.put("prestamos", dinero(prestamos));
        m.put("abonosRecibidos", dinero(abonos));
        return m;
    }

    // ── Ventas por día y por categoría ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> ventasPorDia(LocalDate desde, LocalDate hasta, Integer codti, String username) {
        ReporteService.Alcance alc = reporteService.alcance(codti, username);
        LocalDate fHasta = hasta != null ? hasta : LocalDate.now();
        LocalDate fDesde = desde != null ? desde : fHasta.minusDays(29);
        validarRango(fDesde, fHasta);

        Map<LocalDate, Object[]> porDia = new HashMap<>();
        for (Object[] f : ventaDetalleRepository.ventasPorDia(fDesde.atStartOfDay(), fHasta.atTime(LocalTime.of(23, 59, 59)), alc.codtis())) {
            porDia.put(aFecha(f[0]), f);
        }
        // Un renglón por cada día del rango, también los que no tuvieron ventas
        List<Map<String, Object>> dias = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, utilidad = BigDecimal.ZERO;
        long ventas = 0;
        for (LocalDate dia = fDesde; !dia.isAfter(fHasta); dia = dia.plusDays(1)) {
            Object[] f = porDia.get(dia);
            long n = f == null ? 0 : ((Number) f[1]).longValue();
            BigDecimal monto = f == null ? BigDecimal.ZERO : (BigDecimal) f[2];
            BigDecimal costo = f == null ? BigDecimal.ZERO : (BigDecimal) f[3];
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("dia", dia);
            fila.put("numVentas", n);
            fila.put("totalVenta", dinero(monto));
            fila.put("utilidad", dinero(monto.subtract(costo)));
            dias.add(fila);
            total = total.add(monto);
            utilidad = utilidad.add(monto.subtract(costo));
            ventas += n;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", fDesde);
        r.put("hasta", fHasta);
        r.put("numVentas", ventas);
        r.put("totalVenta", dinero(total));
        r.put("utilidad", dinero(utilidad));
        r.put("dias", dias);
        return r;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ventasPorCategoria(LocalDate desde, LocalDate hasta, Integer codti, String username) {
        ReporteService.Alcance alc = reporteService.alcance(codti, username);
        LocalDate fHasta = hasta != null ? hasta : LocalDate.now();
        LocalDate fDesde = desde != null ? desde : fHasta.withDayOfMonth(1);
        validarRango(fDesde, fHasta);

        List<Map<String, Object>> categorias = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] f : ventaDetalleRepository.ventasPorCategoria(fDesde.atStartOfDay(), fHasta.atTime(LocalTime.of(23, 59, 59)), alc.codtis())) {
            BigDecimal monto = (BigDecimal) f[2];
            BigDecimal costo = (BigDecimal) f[3];
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("categoria", f[0]);
            fila.put("piezas", ((Number) f[1]).longValue());
            fila.put("totalVenta", dinero(monto));
            fila.put("utilidad", dinero(monto.subtract(costo)));
            categorias.add(fila);
            total = total.add(monto);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", fDesde);
        r.put("hasta", fHasta);
        r.put("totalVenta", dinero(total));
        r.put("categorias", categorias);
        return r;
    }

    // ── Accesorios sin movimiento ────────────────────────────────────────────

    /** Accesorios con existencia que no se han vendido en {@code dias} días: dinero parado en la repisa. */
    @Transactional(readOnly = true)
    public Map<String, Object> sinMovimiento(Integer dias, Integer codti, String username) {
        if (dias != null && dias < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los días deben ser al menos 1.");
        int umbral = dias != null ? dias : DIAS_SIN_MOVIMIENTO_DEFAULT;
        ReporteService.Alcance alc = reporteService.alcance(codti, username);

        List<Producto> productos = productoRepository.sinMovimiento(LocalDateTime.now().minusDays(umbral), alc.codtis(),
                PageRequest.of(0, LIMITE_SIN_MOVIMIENTO));
        Map<String, LocalDateTime> ultima = new HashMap<>();
        if (!productos.isEmpty()) {
            List<String> codpros = productos.stream().map(Producto::getCodpro).distinct().toList();
            for (Object[] f : ventaDetalleRepository.ultimaVentaPorArticulo(codpros, alc.codtis())) {
                ultima.put(f[0] + "|" + f[1], (LocalDateTime) f[2]);
            }
        }
        List<Map<String, Object>> filas = new ArrayList<>();
        BigDecimal parado = BigDecimal.ZERO;
        for (Producto p : productos) {
            BigDecimal valor = p.getStock().multiply(p.getPreciopro()).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codpro", p.getCodpro());
            m.put("nombreProducto", p.getProductoMaster().getNombreBase());
            m.put("codti", p.getTienda().getCodti());
            m.put("nombreTienda", p.getTienda().getNombre());
            m.put("stock", p.getStock());
            m.put("valorCosto", valor);
            m.put("ultimaVenta", ultima.get(p.getCodpro() + "|" + p.getTienda().getCodti()));
            filas.add(m);
            parado = parado.add(valor);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dias", umbral);
        r.put("totalArticulos", filas.size());
        r.put("valorParado", dinero(parado));
        r.put("limitado", filas.size() >= LIMITE_SIN_MOVIMIENTO);
        r.put("articulos", filas);
        return r;
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha 'desde' no puede ser posterior a 'hasta'.");
    }

    private static LocalDate aFecha(Object o) {
        if (o instanceof LocalDate l) return l;
        if (o instanceof Date d) return d.toLocalDate();
        return ((java.util.Date) o).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    private static BigDecimal cero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal dinero(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal margen(BigDecimal utilidad, BigDecimal venta) {
        if (venta.signum() == 0) return BigDecimal.ZERO;
        return utilidad.multiply(BigDecimal.valueOf(100)).divide(venta, 1, RoundingMode.HALF_UP);
    }
}
