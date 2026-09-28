package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.reporte.CajaTiendaRow;
import com.skycel.backend.dto.reporte.EquipoRezagadoRow;
import com.skycel.backend.dto.reporte.ResumenTiendaRow;
import com.skycel.backend.dto.reporte.TopProductoRow;
import com.skycel.backend.dto.reporte.ValorInventarioRow;
import com.skycel.backend.repository.MovimientoCajaRepository;
import com.skycel.backend.repository.ProductoImeiRepository;
import com.skycel.backend.repository.ProductoRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.repository.VentaDetalleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reportes gerenciales: comparativo de ventas y utilidad por sucursal, artículos más vendidos y valor del
 * inventario. Un vendedor/técnico no tiene acceso (ver el controller); un encargado solo ve su propia
 * sucursal, igual que ya hace {@code GET /api/ventas/tienda/{codti}}; ROOT/ADMIN pueden pedir una sucursal
 * o "todas" para comparar.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final int TOP_LIMITE = 8;

    private static final int DIAS_REZAGADO_DEFAULT = 60;

    private final VentaDetalleRepository  ventaDetalleRepository;
    private final ProductoRepository      productoRepository;
    private final TiendaRepository        tiendaRepository;
    private final UsuarioRepository       usuarioRepository;
    private final ProductoImeiRepository  productoImeiRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;

    /** A quiénes puede reportar este usuario: su propia sucursal (encargado) o la(s) que pida (ROOT/ADMIN). */
    private record Alcance(List<Tienda> tiendas, List<Integer> codtis, Map<Integer, String> nombrePorCodti) {}

    private Alcance alcance(Integer codti, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        boolean admin = usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN;

        List<Tienda> tiendasDeNegocio = tiendaRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getEsAlmacen()))
                .sorted(Comparator.comparing(Tienda::getNombre))
                .collect(Collectors.toList());

        List<Tienda> tiendas;
        if (!admin) {
            if (usuario.getTienda() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu usuario no tiene una sucursal fija.");
            if (codti != null && !codti.equals(usuario.getTienda().getCodti()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes ver el reporte de tu tienda.");
            tiendas = tiendasDeNegocio.stream().filter(t -> t.getCodti().equals(usuario.getTienda().getCodti())).collect(Collectors.toList());
            if (tiendas.isEmpty()) tiendas = List.of(usuario.getTienda());
        } else if (codti != null) {
            Tienda t = tiendaRepository.findById(codti)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada."));
            tiendas = List.of(t);
        } else {
            tiendas = tiendasDeNegocio;
        }
        if (tiendas.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay sucursales para reportar.");
        List<Integer> codtis = tiendas.stream().map(Tienda::getCodti).collect(Collectors.toList());
        Map<Integer, String> nombrePorCodti = tiendas.stream().collect(Collectors.toMap(Tienda::getCodti, Tienda::getNombre));
        return new Alcance(tiendas, codtis, nombrePorCodti);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> gerencial(LocalDate desde, LocalDate hasta, Integer codti, String username) {
        Alcance alc = alcance(codti, username);
        List<Integer> codtis = alc.codtis();
        Map<Integer, String> nombrePorCodti = alc.nombrePorCodti();

        LocalDate hoy = LocalDate.now();
        LocalDate fDesde = desde != null ? desde : hoy;
        LocalDate fHasta = hasta != null ? hasta : hoy;
        if (fDesde.isAfter(fHasta))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha 'desde' no puede ser posterior a 'hasta'.");
        LocalDateTime desdeDT = fDesde.atStartOfDay();
        LocalDateTime hastaDT = fHasta.atTime(LocalTime.of(23, 59, 59));

        // ── Comparativo por sucursal (con ceros para la que no vendió nada en el rango) ──
        Map<Integer, ResumenTiendaRow> resumenPorCodti = ventaDetalleRepository.resumenPorTienda(desdeDT, hastaDT, codtis).stream()
                .collect(Collectors.toMap(ResumenTiendaRow::getCodti, r -> r));
        List<Map<String, Object>> porTienda = codtis.stream().map(id -> {
            ResumenTiendaRow r = resumenPorCodti.getOrDefault(id,
                    new ResumenTiendaRow(id, nombrePorCodti.get(id), 0L, BigDecimal.ZERO, BigDecimal.ZERO));
            return filaTienda(r);
        }).sorted((a, b) -> ((BigDecimal) b.get("totalVenta")).compareTo((BigDecimal) a.get("totalVenta")))
          .collect(Collectors.toList());

        BigDecimal totalVenta = porTienda.stream().map(m -> (BigDecimal) m.get("totalVenta")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCosto = porTienda.stream().map(m -> (BigDecimal) m.get("totalCosto")).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalVentas = porTienda.stream().mapToLong(m -> (Long) m.get("numVentas")).sum();
        BigDecimal totalUtilidad = totalVenta.subtract(totalCosto);

        // ── Artículos más vendidos ──
        var topCantidad = ventaDetalleRepository.topPorCantidad(desdeDT, hastaDT, codtis, PageRequest.of(0, TOP_LIMITE))
                .stream().map(this::filaProducto).collect(Collectors.toList());
        var topMonto = ventaDetalleRepository.topPorMonto(desdeDT, hastaDT, codtis, PageRequest.of(0, TOP_LIMITE))
                .stream().map(this::filaProducto).collect(Collectors.toList());

        // ── Valor del inventario (a hoy, no depende del rango de fechas) ──
        Map<Integer, ValorInventarioRow> valorPorCodti = productoRepository.valorInventarioPorTienda(codtis).stream()
                .collect(Collectors.toMap(ValorInventarioRow::getCodti, r -> r));
        List<Map<String, Object>> inventario = codtis.stream().map(id -> {
            ValorInventarioRow r = valorPorCodti.getOrDefault(id,
                    new ValorInventarioRow(id, nombrePorCodti.get(id), BigDecimal.ZERO, BigDecimal.ZERO, 0L));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codti", r.getCodti());
            m.put("nombreTienda", r.getNombreTienda());
            m.put("numProductos", r.getNumProductos());
            m.put("valorCosto", r.getValorCosto());
            m.put("valorVenta", r.getValorVenta());
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("valorCosto")).compareTo((BigDecimal) a.get("valorCosto")))
          .collect(Collectors.toList());
        BigDecimal inventarioCosto = inventario.stream().map(m -> (BigDecimal) m.get("valorCosto")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal inventarioVenta = inventario.stream().map(m -> (BigDecimal) m.get("valorVenta")).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("desde", fDesde);
        resultado.put("hasta", fHasta);
        resultado.put("comparativo", porTienda.size() > 1);
        resultado.put("totalVentas", totalVentas);
        resultado.put("totalVenta", totalVenta);
        resultado.put("totalCosto", totalCosto);
        resultado.put("totalUtilidad", totalUtilidad);
        resultado.put("margenPorciento", margen(totalUtilidad, totalVenta));
        resultado.put("porTienda", porTienda);
        resultado.put("topPorCantidad", topCantidad);
        resultado.put("topPorMonto", topMonto);
        resultado.put("inventario", inventario);
        resultado.put("inventarioValorCosto", inventarioCosto);
        resultado.put("inventarioValorVenta", inventarioVenta);
        return resultado;
    }

    /** Equipos (celulares/tablets) disponibles que llevan {@code dias} (60 por defecto) sin venderse, o que
     *  alguien marcó a mano como rezagados ({@code producto.rezagado}) aunque sean recientes. */
    @Transactional(readOnly = true)
    public Map<String, Object> equiposRezagados(Integer dias, Integer codti, String username) {
        if (dias != null && dias < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los días deben ser al menos 1.");
        int diasEfectivo = dias != null ? dias : DIAS_REZAGADO_DEFAULT;
        Alcance alc = alcance(codti, username);
        LocalDateTime limite = LocalDateTime.now().minusDays(diasEfectivo);

        List<Map<String, Object>> equipos = productoImeiRepository.rezagados(limite, alc.codtis()).stream()
                .map(r -> filaEquipo(r, diasEfectivo))
                .collect(Collectors.toList());

        Map<Integer, Long> conteoPorTienda = equipos.stream()
                .collect(Collectors.groupingBy(m -> (Integer) m.get("codti"), Collectors.counting()));
        List<Map<String, Object>> porTienda = alc.codtis().stream().map(id -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codti", id);
            m.put("nombreTienda", alc.nombrePorCodti().get(id));
            m.put("numEquipos", conteoPorTienda.getOrDefault(id, 0L));
            return m;
        }).sorted((a, b) -> ((Long) b.get("numEquipos")).compareTo((Long) a.get("numEquipos")))
          .collect(Collectors.toList());

        BigDecimal valorCostoTotal = equipos.stream().map(m -> (BigDecimal) m.get("costo")).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("dias", diasEfectivo);
        resultado.put("totalEquipos", equipos.size());
        resultado.put("valorCostoTotal", valorCostoTotal);
        resultado.put("porTienda", porTienda);
        resultado.put("equipos", equipos);
        return resultado;
    }

    private Map<String, Object> filaEquipo(EquipoRezagadoRow r, int diasUmbral) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idProductoImei", r.getIdProductoImei());
        m.put("imei", r.getImei());
        m.put("codti", r.getCodti());
        m.put("nombreTienda", r.getNombreTienda());
        m.put("codpro", r.getCodpro());
        m.put("nombreArticulo", r.getNombreArticulo());
        m.put("condicion", r.getCondicion());
        m.put("fechaRegistro", r.getFechaRegistro());
        m.put("diasEnExistencia", ChronoUnit.DAYS.between(r.getFechaRegistro(), LocalDateTime.now()));
        m.put("precio", r.getPrecio());
        m.put("costo", r.getCosto());
        m.put("marcadoManual", r.isMarcadoManual());
        m.put("detectadoPorAntiguedad", !r.getFechaRegistro().isAfter(LocalDateTime.now().minusDays(diasUmbral)));
        return m;
    }

    /** Saldo y movimientos de hoy de TODAS las cajas de cada sucursal, en una sola vista. Solo ROOT/ADMIN
     *  (ver el controller); un encargado ya tiene el saldo de su propia caja en la pantalla de Caja. */
    @Transactional(readOnly = true)
    public Map<String, Object> cajaConsolidada(String username) {
        Alcance alc = alcance(null, username);
        LocalDateTime hoyInicio = LocalDate.now().atStartOfDay();
        Map<Integer, CajaTiendaRow> porCodti = movimientoCajaRepository.consolidadoPorTienda(alc.codtis(), hoyInicio).stream()
                .collect(Collectors.toMap(CajaTiendaRow::getCodti, r -> r));

        List<Map<String, Object>> porTienda = alc.codtis().stream().map(id -> {
            CajaTiendaRow r = porCodti.getOrDefault(id, new CajaTiendaRow(id, alc.nombrePorCodti().get(id), 0L,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            return filaCaja(r);
        }).sorted((a, b) -> ((BigDecimal) b.get("saldo")).compareTo((BigDecimal) a.get("saldo")))
          .collect(Collectors.toList());

        BigDecimal saldoGeneral = porTienda.stream().map(m -> (BigDecimal) m.get("saldo")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal entradasHoyTotal = porTienda.stream().map(m -> (BigDecimal) m.get("entradasHoy")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salidasHoyTotal = porTienda.stream().map(m -> (BigDecimal) m.get("salidasHoy")).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("saldoGeneral", saldoGeneral);
        resultado.put("entradasHoyTotal", entradasHoyTotal);
        resultado.put("salidasHoyTotal", salidasHoyTotal);
        resultado.put("saldoHoyTotal", entradasHoyTotal.subtract(salidasHoyTotal));
        resultado.put("porTienda", porTienda);
        return resultado;
    }

    private Map<String, Object> filaCaja(CajaTiendaRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codti", r.getCodti());
        m.put("nombreTienda", r.getNombreTienda());
        m.put("numCajas", r.getNumCajas());
        m.put("saldo", r.getSaldo());
        m.put("entradasHoy", r.getEntradasHoy());
        m.put("salidasHoy", r.getSalidasHoy());
        m.put("saldoHoy", r.getSaldoHoy());
        return m;
    }

    private Map<String, Object> filaTienda(ResumenTiendaRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codti", r.getCodti());
        m.put("nombreTienda", r.getNombreTienda());
        m.put("numVentas", r.getNumVentas());
        m.put("totalVenta", r.getTotalVenta());
        m.put("totalCosto", r.getTotalCosto());
        m.put("utilidad", r.getUtilidad());
        m.put("margenPorciento", r.getMargenPorciento());
        return m;
    }

    private Map<String, Object> filaProducto(TopProductoRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idprodmaster", r.getIdprodmaster());
        m.put("nombreProducto", r.getNombreProducto());
        m.put("unidades", r.getUnidades());
        m.put("montoVenta", r.getMontoVenta());
        return m;
    }

    private BigDecimal margen(BigDecimal utilidad, BigDecimal venta) {
        if (venta.signum() == 0) return BigDecimal.ZERO;
        return utilidad.multiply(BigDecimal.valueOf(100)).divide(venta, 1, RoundingMode.HALF_UP);
    }
}
