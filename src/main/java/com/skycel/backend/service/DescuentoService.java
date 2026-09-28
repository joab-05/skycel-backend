package com.skycel.backend.service;

import com.skycel.backend.domain.entity.DescuentoRegla;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.descuento.DescuentoReglaRequestDto;
import com.skycel.backend.repository.DescuentoReglaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Descuentos automáticos del punto de venta: el sistema decide si aplican y cuánto, un vendedor o un
 * administrador nunca los capturan a mano al vender (ver {@link VentaService#resolverLinea}). Se
 * administran desde {@code #/descuentos} (solo ROOT/ADMIN).
 */
@Service
@RequiredArgsConstructor
public class DescuentoService {

    private static final byte SIEMPRE = 0;
    private static final byte DIAS_SEMANA = 1;
    private static final byte RANGO_FECHA = 2;

    private static final Map<DayOfWeek, String> DIA_CORTO = Map.of(
            DayOfWeek.MONDAY, "LUN", DayOfWeek.TUESDAY, "MAR", DayOfWeek.WEDNESDAY, "MIE",
            DayOfWeek.THURSDAY, "JUE", DayOfWeek.FRIDAY, "VIE", DayOfWeek.SATURDAY, "SAB", DayOfWeek.SUNDAY, "DOM");

    private final DescuentoReglaRepository repository;

    // ── Cálculo (el POS y la venta lo usan) ─────────────────────────────────────

    /** El descuento (en pesos, ≥ 0) que aplica hoy a este producto vendido en {@code precioLista}. 0 si ninguna regla aplica. */
    @Transactional(readOnly = true)
    public BigDecimal calcularDescuento(Producto producto, BigDecimal precioLista, LocalDateTime momento) {
        if (precioLista == null || precioLista.signum() <= 0) return BigDecimal.ZERO;
        TipoProducto tipo = producto.getProductoMaster() != null ? producto.getProductoMaster().getTipo() : null;
        Integer idProdMaster = producto.getProductoMaster() != null ? producto.getProductoMaster().getIdprodmaster() : null;
        Integer codti = producto.getTienda() != null ? producto.getTienda().getCodti() : null;

        List<DescuentoRegla> candidatas = repository.findByActivoTrue().stream()
                .filter(r -> aplicaAlAlcance(r, tipo, idProdMaster, codti))
                .filter(r -> r.getMontoMinimo() == null || r.getMontoMinimo().signum() <= 0
                        || precioLista.compareTo(r.getMontoMinimo()) >= 0)
                .filter(r -> aplicaHoy(r, momento))
                .collect(Collectors.toList());

        BigDecimal mejor = BigDecimal.ZERO;
        for (DescuentoRegla r : candidatas) {
            BigDecimal d = montoDescuento(r, precioLista);
            if (d.compareTo(mejor) > 0) mejor = d;
        }
        // nunca deja el precio en $0 o negativo
        BigDecimal tope = precioLista.subtract(new BigDecimal("0.01"));
        if (mejor.compareTo(tope) > 0) mejor = tope.max(BigDecimal.ZERO);
        return mejor;
    }

    static boolean aplicaAlAlcance(DescuentoRegla r, TipoProducto tipo, Integer idProdMaster, Integer codti) {
        if (r.getTipoProducto() != null && r.getTipoProducto() != tipo) return false;
        if (r.getIdProductoMaster() != null && !r.getIdProductoMaster().equals(idProdMaster)) return false;
        if (r.getCodti() != null && !r.getCodti().equals(codti)) return false;
        return true;
    }

    static boolean aplicaHoy(DescuentoRegla r, LocalDateTime momento) {
        byte aplicacion = r.getAplicacion() != null ? r.getAplicacion() : SIEMPRE;
        return switch (aplicacion) {
            case DIAS_SEMANA -> r.getDiasSemana() != null
                    && r.getDiasSemana().contains(DIA_CORTO.get(momento.getDayOfWeek()));
            case RANGO_FECHA -> r.getFechaInicio() != null && r.getFechaFin() != null
                    && !momento.toLocalDate().isBefore(r.getFechaInicio())
                    && !momento.toLocalDate().isAfter(r.getFechaFin());
            default -> true; // SIEMPRE
        };
    }

    static BigDecimal montoDescuento(DescuentoRegla r, BigDecimal precioLista) {
        if (r.getDescuentoFijo() != null && r.getDescuentoFijo().signum() > 0) return r.getDescuentoFijo();
        if (r.getDescuentoPorcentaje() != null && r.getDescuentoPorcentaje().signum() > 0) {
            return precioLista.multiply(r.getDescuentoPorcentaje())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    // ── Administración (ROOT/ADMIN) ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return repository.findAllByOrderByFechaRegistroDesc().stream().map(this::toMap).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> crear(DescuentoReglaRequestDto dto) {
        DescuentoRegla r = new DescuentoRegla();
        aplicarCambios(r, dto);
        return toMap(repository.save(r));
    }

    @Transactional
    public Map<String, Object> actualizar(Integer id, DescuentoReglaRequestDto dto) {
        DescuentoRegla r = buscarOFallar(id);
        aplicarCambios(r, dto);
        return toMap(repository.save(r));
    }

    @Transactional
    public Map<String, Object> cambiarActivo(Integer id, boolean activo) {
        DescuentoRegla r = buscarOFallar(id);
        r.setActivo(activo);
        return toMap(repository.save(r));
    }

    private void aplicarCambios(DescuentoRegla r, DescuentoReglaRequestDto dto) {
        if (dto.getNombre() == null || dto.getNombre().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica un nombre para la regla.");
        boolean tieneFijo = dto.getDescuentoFijo() != null && dto.getDescuentoFijo().signum() > 0;
        boolean tienePorciento = dto.getDescuentoPorcentaje() != null && dto.getDescuentoPorcentaje().signum() > 0;
        if (tieneFijo == tienePorciento)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indica un descuento fijo en pesos O un porcentaje, exactamente uno de los dos.");
        if (tienePorciento && dto.getDescuentoPorcentaje().compareTo(BigDecimal.valueOf(100)) >= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El porcentaje de descuento debe ser menor a 100.");

        TipoProducto tipo = null;
        if (dto.getTipoProducto() != null && !dto.getTipoProducto().isBlank()) {
            try {
                tipo = TipoProducto.valueOf(dto.getTipoProducto().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de artículo no válido: " + dto.getTipoProducto());
            }
        }

        byte aplicacion = SIEMPRE;
        String dias = null;
        if ("DIAS_SEMANA".equalsIgnoreCase(dto.getAplicacion())) {
            aplicacion = DIAS_SEMANA;
            if (dto.getDiasSemana() == null || dto.getDiasSemana().isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica al menos un día de la semana.");
            dias = dto.getDiasSemana().stream().map(d -> d.trim().toUpperCase()).distinct()
                    .collect(Collectors.joining(","));
        } else if ("RANGO_FECHA".equalsIgnoreCase(dto.getAplicacion())) {
            aplicacion = RANGO_FECHA;
            if (dto.getFechaInicio() == null || dto.getFechaFin() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la fecha de inicio y de fin.");
            if (dto.getFechaInicio().isAfter(dto.getFechaFin()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de inicio no puede ser posterior a la de fin.");
        }

        r.setNombre(dto.getNombre().trim());
        r.setTipoProducto(tipo);
        r.setIdProductoMaster(dto.getIdProductoMaster());
        r.setCodti(dto.getCodti());
        r.setMontoMinimo(dto.getMontoMinimo());
        r.setDescuentoPorcentaje(tienePorciento ? dto.getDescuentoPorcentaje() : null);
        r.setDescuentoFijo(tieneFijo ? dto.getDescuentoFijo() : null);
        r.setAplicacion(aplicacion);
        r.setDiasSemana(dias);
        r.setFechaInicio(aplicacion == RANGO_FECHA ? dto.getFechaInicio() : null);
        r.setFechaFin(aplicacion == RANGO_FECHA ? dto.getFechaFin() : null);
        r.setActivo(dto.getActivo() == null || dto.getActivo());
    }

    private DescuentoRegla buscarOFallar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regla de descuento no encontrada: " + id));
    }

    private Map<String, Object> toMap(DescuentoRegla r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("iddescuento", r.getIddescuento());
        m.put("nombre", r.getNombre());
        m.put("tipoProducto", r.getTipoProducto());
        m.put("idProductoMaster", r.getIdProductoMaster());
        m.put("codti", r.getCodti());
        m.put("montoMinimo", r.getMontoMinimo());
        m.put("descuentoPorcentaje", r.getDescuentoPorcentaje());
        m.put("descuentoFijo", r.getDescuentoFijo());
        m.put("aplicacion", switch (r.getAplicacion() != null ? r.getAplicacion() : SIEMPRE) {
            case DIAS_SEMANA -> "DIAS_SEMANA";
            case RANGO_FECHA -> "RANGO_FECHA";
            default -> "SIEMPRE";
        });
        m.put("diasSemana", r.getDiasSemana() != null ? List.of(r.getDiasSemana().split(",")) : List.of());
        m.put("fechaInicio", r.getFechaInicio());
        m.put("fechaFin", r.getFechaFin());
        m.put("activo", r.getActivo());
        m.put("fechaRegistro", r.getFechaRegistro());
        return m;
    }
}
