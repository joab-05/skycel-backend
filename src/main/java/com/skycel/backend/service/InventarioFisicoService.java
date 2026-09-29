package com.skycel.backend.service;

import com.skycel.backend.domain.entity.InventarioAuditoria;
import com.skycel.backend.domain.entity.InventarioAuditoriaDetalle;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.InventarioAuditoriaDetalleRepository;
import com.skycel.backend.repository.InventarioAuditoriaRepository;
import com.skycel.backend.repository.ProductoRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inventario físico por secciones: el flujo Aperturar → Inventariar (escanear) → Catalogar faltantes/sobrantes
 * → Finalizar del sistema anterior, sobre las tablas {@code inventario_auditoria}/{@code inventario_auditoria_detalle}
 * que ya existían en el esquema (heredadas, sin capa de servicio hasta ahora).
 */
@Service
@RequiredArgsConstructor
public class InventarioFisicoService {

    // Estado de InventarioAuditoria
    public static final byte ESTADO_ABIERTO = 0;
    public static final byte ESTADO_CATALOGANDO = 1;
    public static final byte ESTADO_FINALIZADO = 2;

    // Estado de InventarioAuditoriaDetalle (solo relevante cuando diferencia != 0)
    public static final byte DETALLE_PENDIENTE = 0;
    public static final byte DETALLE_AJUSTADO = 1;
    public static final byte DETALLE_JUSTIFICADO = 2;

    private static final List<Byte> ESTADOS_ACTIVOS = List.of(ESTADO_ABIERTO, ESTADO_CATALOGANDO);

    private final InventarioAuditoriaRepository auditoriaRepository;
    private final InventarioAuditoriaDetalleRepository detalleRepository;
    private final ProductoRepository productoRepository;
    private final TiendaRepository tiendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoInventarioService movimientoInventarioService;

    @Transactional
    public Map<String, Object> abrir(Integer codti, Integer idusuarioEncargado, String username) {
        Usuario auditor = usuarioPorUsername(username);
        Tienda tienda = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada: " + codti));
        validarAcceso(auditor, codti);

        auditoriaRepository.findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(codti, ESTADOS_ACTIVOS)
                .ifPresent(a -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya hay una auditoría de inventario en curso para esta sucursal (#" + a.getIdinventario() + ")."); });

        Usuario encargado = idusuarioEncargado != null
                ? usuarioRepository.findById(idusuarioEncargado)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario encargado no encontrado."))
                : auditor;

        InventarioAuditoria a = auditoriaRepository.save(InventarioAuditoria.builder()
                .tienda(tienda)
                .usuarioAuditor(auditor)
                .usuarioEncargadoTienda(encargado)
                .estado(ESTADO_ABIERTO)
                .fechaInicio(LocalDateTime.now())
                .totalFaltantes(0)
                .totalSobrantes(0)
                .build());
        return toMap(a, List.of());
    }

    @Transactional
    public Map<String, Object> escanear(Integer idinventario, String codpro, Integer cantidad, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());
        if (a.getEstado() != ESTADO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta auditoría ya no admite conteo (fase actual: " + nombreEstado(a.getEstado()) + ").");
        int cant = cantidad != null && cantidad > 0 ? cantidad : 1;

        Producto p = productoRepository.findByCodproAndTienda_Codti(codpro, a.getTienda().getCodti())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El código '" + codpro + "' no existe en " + a.getTienda().getNombre() + "."));
        if (p.getSeccion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + codpro + "' no tiene una sección asignada. Asígnale una en Inventario > Catálogos antes de inventariarlo.");

        InventarioAuditoriaDetalle d = detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(idinventario, codpro)
                .orElseGet(() -> InventarioAuditoriaDetalle.builder()
                        .inventarioAuditoria(a)
                        .seccion(p.getSeccion())
                        .codpro(codpro)
                        .conteo((short) 0)
                        .stockSistema(stockComoShort(p.getStock()))
                        .estado(DETALLE_PENDIENTE)
                        .build());
        d.setConteo((short) (d.getConteo() + cant));
        d = detalleRepository.save(d);
        return toMapDetalle(d, p.getProductoMaster() != null ? p.getProductoMaster().getNombreBase() : null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Integer idinventario, boolean soloDiferencias, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());

        List<InventarioAuditoriaDetalle> filas = detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(idinventario);
        Map<String, String> nombrePorCodpro = productoRepository.findByCodproInAndTienda_Codti(
                        filas.stream().map(InventarioAuditoriaDetalle::getCodpro).distinct().collect(Collectors.toList()),
                        a.getTienda().getCodti())
                .stream().collect(Collectors.toMap(Producto::getCodpro,
                        p -> p.getProductoMaster() != null ? p.getProductoMaster().getNombreBase() : p.getCodpro(),
                        (x, y) -> x));

        List<Map<String, Object>> items = filas.stream()
                .filter(d -> !soloDiferencias || diferenciaDe(d) != 0)
                .map(d -> toMapDetalle(d, nombrePorCodpro.get(d.getCodpro())))
                .collect(Collectors.toList());

        Map<String, Object> resultado = new LinkedHashMap<>(toMap(a, filas));
        resultado.put("items", items);
        return resultado;
    }

    @Transactional
    public Map<String, Object> cerrarConteo(Integer idinventario, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());
        if (a.getEstado() != ESTADO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta auditoría no está en fase de conteo.");
        a.setEstado(ESTADO_CATALOGANDO);
        a = auditoriaRepository.save(a);
        return toMap(a, detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(idinventario));
    }

    @Transactional
    public Map<String, Object> resolverDetalle(Integer idinventario, Integer iddetalleInv, String accion, String motivo, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());
        if (a.getEstado() != ESTADO_CATALOGANDO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se catalogan diferencias mientras la auditoría está en esa fase.");

        InventarioAuditoriaDetalle d = detalleRepository.findById(iddetalleInv)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detalle no encontrado."));
        if (!d.getInventarioAuditoria().getIdinventario().equals(idinventario))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese detalle no pertenece a esta auditoría.");
        int diferencia = diferenciaDe(d);
        if (diferencia == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este artículo no tuvo diferencia; no hay nada que catalogar.");

        if ("AJUSTAR".equalsIgnoreCase(accion)) {
            Producto p = productoRepository.findByCodproAndTienda_Codti(d.getCodpro(), a.getTienda().getCodti())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El artículo ya no existe en esta sucursal."));
            BigDecimal antes = p.getStock() != null ? p.getStock() : BigDecimal.ZERO;
            p.setStock(BigDecimal.valueOf(d.getConteo()));
            productoRepository.save(p);
            movimientoInventarioService.registrar(p, antes, "AJUSTE",
                    "Ajuste por inventario físico #" + idinventario, "Inventario físico #" + idinventario);
            d.setEstado(DETALLE_AJUSTADO);
        } else if ("JUSTIFICAR".equalsIgnoreCase(accion)) {
            if (motivo == null || motivo.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo para justificar la diferencia sin ajustar el stock.");
            d.setEstado(DETALLE_JUSTIFICADO);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción no válida. Use AJUSTAR o JUSTIFICAR.");
        }
        d = detalleRepository.save(d);
        return toMapDetalle(d, null);
    }

    @Transactional
    public Map<String, Object> finalizar(Integer idinventario, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());
        if (a.getEstado() != ESTADO_CATALOGANDO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se finaliza una auditoría que ya cerró su conteo.");

        List<InventarioAuditoriaDetalle> filas = detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(idinventario);
        long pendientes = filas.stream().filter(d -> diferenciaDe(d) != 0 && d.getEstado() == DETALLE_PENDIENTE).count();
        if (pendientes > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aún hay " + pendientes + " diferencia(s) sin catalogar (ajustar o justificar).");

        int faltantes = (int) filas.stream().filter(d -> diferenciaDe(d) < 0).count();
        int sobrantes = (int) filas.stream().filter(d -> diferenciaDe(d) > 0).count();
        a.setTotalFaltantes(faltantes);
        a.setTotalSobrantes(sobrantes);
        a.setEstado(ESTADO_FINALIZADO);
        a.setFechaFin(LocalDateTime.now());
        a = auditoriaRepository.save(a);
        return toMap(a, filas);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> activa(Integer codti, String username) {
        validarAcceso(usuarioPorUsername(username), codti);
        return auditoriaRepository.findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(codti, ESTADOS_ACTIVOS)
                .map(a -> toMap(a, detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(a.getIdinventario())))
                .orElseGet(() -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("activa", false); return m; });
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historial(Integer codti, String username) {
        validarAcceso(usuarioPorUsername(username), codti);
        return auditoriaRepository.findByTienda_CodtiOrderByFechaInicioDesc(codti).stream()
                .map(a -> toMap(a, List.of()))
                .collect(Collectors.toList());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private InventarioAuditoria auditoriaDe(Integer idinventario) {
        return auditoriaRepository.findById(idinventario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Auditoría de inventario no encontrada: " + idinventario));
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private void validarAcceso(Usuario usuario, Integer codti) {
        boolean admin = usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN;
        if (admin) return;
        if (usuario.getTienda() == null || !usuario.getTienda().getCodti().equals(codti))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes operar el inventario físico de tu propia sucursal.");
    }

    private static short stockComoShort(BigDecimal stock) {
        if (stock == null) return 0;
        long v = stock.longValue();
        if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
        if (v < Short.MIN_VALUE) v = Short.MIN_VALUE;
        return (short) v;
    }

    private static int diferenciaDe(InventarioAuditoriaDetalle d) {
        int conteo = d.getConteo() != null ? d.getConteo() : 0;
        int sistema = d.getStockSistema() != null ? d.getStockSistema() : 0;
        return conteo - sistema;
    }

    private static String nombreEstado(Byte estado) {
        if (estado == null) return "desconocido";
        return switch (estado) {
            case ESTADO_ABIERTO -> "Aperturada (inventariando)";
            case ESTADO_CATALOGANDO -> "Catalogando diferencias";
            case ESTADO_FINALIZADO -> "Finalizada";
            default -> "Desconocido";
        };
    }

    private Map<String, Object> toMap(InventarioAuditoria a, List<InventarioAuditoriaDetalle> filas) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activa", true);
        m.put("idinventario", a.getIdinventario());
        m.put("codti", a.getTienda().getCodti());
        m.put("nombreTienda", a.getTienda().getNombre());
        m.put("nombreAuditor", a.getUsuarioAuditor().getNombreCompleto());
        m.put("nombreEncargado", a.getUsuarioEncargadoTienda().getNombreCompleto());
        m.put("estado", a.getEstado());
        m.put("estadoDisplay", nombreEstado(a.getEstado()));
        m.put("fechaInicio", a.getFechaInicio());
        m.put("fechaFin", a.getFechaFin());
        m.put("totalFaltantes", a.getTotalFaltantes());
        m.put("totalSobrantes", a.getTotalSobrantes());
        m.put("totalArticulos", filas.size());
        m.put("totalConDiferencia", filas.stream().filter(d -> diferenciaDe(d) != 0).count());
        m.put("totalPendientes", filas.stream().filter(d -> diferenciaDe(d) != 0 && d.getEstado() == DETALLE_PENDIENTE).count());
        return m;
    }

    private Map<String, Object> toMapDetalle(InventarioAuditoriaDetalle d, String nombreArticulo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("iddetalleInv", d.getIddetalleInv());
        m.put("codpro", d.getCodpro());
        m.put("nombreArticulo", nombreArticulo);
        m.put("idseccion", d.getSeccion() != null ? d.getSeccion().getIdseccion() : null);
        m.put("nombreSeccion", d.getSeccion() != null ? d.getSeccion().getNombre() : null);
        m.put("conteo", d.getConteo());
        m.put("stockSistema", d.getStockSistema());
        m.put("diferencia", diferenciaDe(d));
        m.put("estado", d.getEstado());
        return m;
    }
}
