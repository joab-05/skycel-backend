package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.reporte.ResumenTiendaRow;
import com.skycel.backend.dto.reporte.TopProductoRow;
import com.skycel.backend.dto.reporte.ValorInventarioRow;
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

    private final VentaDetalleRepository ventaDetalleRepository;
    private final ProductoRepository     productoRepository;
    private final TiendaRepository       tiendaRepository;
    private final UsuarioRepository      usuarioRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> gerencial(LocalDate desde, LocalDate hasta, Integer codti, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        boolean admin = usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN;

        List<Tienda> tiendasDeNegocio = tiendaRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getEsAlmacen()))
                .sorted(Comparator.comparing(Tienda::getNombre))
                .collect(Collectors.toList());

        List<Tienda> alcance;
        if (!admin) {
            if (usuario.getTienda() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu usuario no tiene una sucursal fija.");
            if (codti != null && !codti.equals(usuario.getTienda().getCodti()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes ver el reporte de tu tienda.");
            alcance = tiendasDeNegocio.stream().filter(t -> t.getCodti().equals(usuario.getTienda().getCodti())).collect(Collectors.toList());
            if (alcance.isEmpty()) alcance = List.of(usuario.getTienda());
        } else if (codti != null) {
            Tienda t = tiendaRepository.findById(codti)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada."));
            alcance = List.of(t);
        } else {
            alcance = tiendasDeNegocio;
        }
        if (alcance.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay sucursales para reportar.");
        List<Integer> codtis = alcance.stream().map(Tienda::getCodti).collect(Collectors.toList());
        Map<Integer, String> nombrePorCodti = alcance.stream().collect(Collectors.toMap(Tienda::getCodti, Tienda::getNombre));

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
