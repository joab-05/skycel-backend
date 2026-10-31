package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Permiso;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inventario físico por secciones, en dos manos:
 * <ol>
 *   <li><b>El encargado cuenta</b> (aperturar → escanear → enviar a revisión). Eso <b>no cambia las existencias</b>: genera un
 *       resumen de lo que contó contra lo que dice el sistema.</li>
 *   <li><b>El administrador confirma</b> (decide qué ajustar y qué justificar y cierra). Hasta que lo cierra no cambia nada;
 *       al cerrarlo, las diferencias que decidió ajustar se aplican a las existencias.</li>
 * </ol>
 * Los accesorios se cuentan por código; los equipos (celulares y tablets), uno por uno por IMEI o serie.
 */
@Service
@RequiredArgsConstructor
public class InventarioFisicoService {

    // Estado de InventarioAuditoria
    public static final byte ESTADO_ABIERTO = 0;
    /** Conteo enviado: el administrador lo revisa (antes se llamaba «catalogando diferencias»). */
    public static final byte ESTADO_CATALOGANDO = 1;
    public static final byte ESTADO_EN_REVISION = ESTADO_CATALOGANDO;
    public static final byte ESTADO_FINALIZADO = 2;
    public static final byte ESTADO_CANCELADO = 3;

    // Estado de InventarioAuditoriaDetalle (solo relevante cuando diferencia != 0)
    public static final byte DETALLE_PENDIENTE = 0;
    /** Decidido ajustar: las existencias cambian cuando el administrador cierra el inventario. */
    public static final byte DETALLE_AJUSTADO = 1;
    public static final byte DETALLE_JUSTIFICADO = 2;

    private static final List<Byte> ESTADOS_ACTIVOS = List.of(ESTADO_ABIERTO, ESTADO_EN_REVISION);

    private final InventarioAuditoriaRepository auditoriaRepository;
    private final InventarioAuditoriaDetalleRepository detalleRepository;
    private final InventarioAuditoriaUnidadRepository unidadRepository;
    private final ProductoRepository productoRepository;
    private final ProductoImeiRepository imeiRepository;
    private final TiendaRepository tiendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoInventarioService movimientoInventarioService;
    private final NotificacionService notificacionService;
    private final PermisoService permisoService;
    private final CategoriaRepository categoriaRepository;

    // ── Contar (encargado) ───────────────────────────────────────────────────

    public Map<String, Object> abrir(Integer codti, Integer idusuarioEncargado, String username) {
        return abrir(codti, idusuarioEncargado, null, username);
    }

    /**
     * @param categorias las categorías que cubre el inventario (por ejemplo solo equipos, o solo micas); vacío = sin alcance
     */
    @Transactional
    public Map<String, Object> abrir(Integer codti, Integer idusuarioEncargado, List<Short> categorias, String username) {
        Usuario auditor = usuarioPorUsername(username);
        Tienda tienda = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada: " + codti));
        validarAcceso(auditor, codti);

        auditoriaRepository.findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(codti, ESTADOS_ACTIVOS)
                .ifPresent(a -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya hay un inventario en curso para esta sucursal (#" + a.getIdinventario() + ")."); });

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
                .alcance(alcanceDeCategorias(categorias))
                .totalFaltantes(0)
                .totalSobrantes(0)
                .build());
        return toMap(a, List.of());
    }

    /** Cuenta una pieza (o {@code cantidad}) de un accesorio por su código, o un equipo por su IMEI o serie. */
    @Transactional
    public Map<String, Object> escanear(Integer idinventario, String codigo, Integer cantidad, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());
        if (a.getEstado() != ESTADO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este inventario ya no admite conteo (fase actual: " + nombreEstado(a.getEstado()) + ").");
        int cant = cantidad != null && cantidad > 0 ? cantidad : 1;
        String leido = codigo == null ? "" : codigo.trim();

        Optional<Producto> producto = productoRepository.findByCodproAndTienda_Codti(leido, a.getTienda().getCodti());
        if (producto.isEmpty()) {
            Optional<ProductoImei> unidad = imeiRepository.findByImei(leido.replaceAll("[\\s-]", "").toUpperCase());
            if (unidad.isPresent()) return escanearEquipo(a, unidad.get());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "El código o IMEI '" + leido + "' no existe en " + a.getTienda().getNombre() + ".");
        }
        Producto p = producto.get();
        Set<Short> alcance = alcanceDe(a);
        if (!alcance.isEmpty()) {
            if (!enAlcance(p.getProductoMaster(), alcance))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + leido + "' no pertenece a lo que cubre este inventario ("
                        + nombresDelAlcance(alcance) + ").");
        } else if (p.getSeccion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + leido + "' no tiene una sección asignada. Asígnale una en Inventario > Catálogos antes de inventariarlo.");

        InventarioAuditoriaDetalle d = detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(idinventario, leido)
                .orElseGet(() -> InventarioAuditoriaDetalle.builder()
                        .inventarioAuditoria(a)
                        .seccion(p.getSeccion())
                        .codpro(leido)
                        .conteo((short) 0)
                        .stockSistema(stockComoShort(p.getStock()))
                        .estado(DETALLE_PENDIENTE)
                        .build());
        d.setConteo((short) (d.getConteo() + cant));
        d = detalleRepository.save(d);
        return toMapDetalle(d, p.getProductoMaster() != null ? p.getProductoMaster().getNombreBase() : null);
    }

    private Map<String, Object> escanearEquipo(InventarioAuditoria a, ProductoImei u) {
        Integer idinventario = a.getIdinventario();
        if (unidadRepository.findByInventarioAuditoria_IdinventarioAndImei(idinventario, u.getImei()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El equipo '" + u.getImei() + "' ya se contó en este inventario.");
        ProductoMaster master = u.getProductoMaster();
        Set<Short> alcance = alcanceDe(a);
        if (!alcance.isEmpty() && !enAlcance(master, alcance))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El equipo '" + u.getImei() + "' (" + master.getNombreBase()
                    + ") no pertenece a lo que cubre este inventario (" + nombresDelAlcance(alcance) + ").");
        boolean enSistema = ProductoImei.DISPONIBLE.equals(u.getEstado()) && u.getTienda() != null
                && u.getTienda().getCodti().equals(a.getTienda().getCodti());

        String clave = claveEquipo(master);
        InventarioAuditoriaDetalle d = detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(idinventario, clave)
                .orElseGet(() -> InventarioAuditoriaDetalle.builder()
                        .inventarioAuditoria(a).productoMaster(master).codpro(clave)
                        .conteo((short) 0)
                        .stockSistema((short) imeiRepository.countByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(
                                master.getIdprodmaster(), a.getTienda().getCodti(), ProductoImei.DISPONIBLE))
                        .estado(DETALLE_PENDIENTE).build());
        d.setConteo((short) (d.getConteo() + 1));
        d = detalleRepository.save(d);
        unidadRepository.save(InventarioAuditoriaUnidad.builder()
                .inventarioAuditoria(a).productoMaster(master).imei(u.getImei()).enSistema(enSistema).build());

        Map<String, Object> m = toMapDetalle(d, master.getNombreBase());
        m.put("imei", u.getImei());
        m.put("enSistema", enSistema);
        if (!enSistema) {
            m.put("advertencia", "El sistema no lo tiene disponible en esta sucursal (estado: " + u.getEstado()
                    + (u.getTienda() != null ? ", sucursal: " + u.getTienda().getNombre() : "") + "). Se cuenta como sobrante.");
        }
        return m;
    }

    /** Corrige a mano el conteo de un accesorio mientras se está contando. */
    @Transactional
    public Map<String, Object> corregirConteo(Integer idinventario, Integer iddetalleInv, Integer conteo, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());
        if (a.getEstado() != ESTADO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se corrige el conteo mientras se está contando.");
        InventarioAuditoriaDetalle d = detalleDe(idinventario, iddetalleInv);
        if (d.getProductoMaster() != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los equipos se cuentan por IMEI: quita el equipo mal escaneado.");
        if (conteo == null || conteo < 0 || conteo > Short.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El conteo debe ser un número entre 0 y " + Short.MAX_VALUE + ".");
        d.setConteo(conteo.shortValue());
        return toMapDetalle(detalleRepository.save(d), null);
    }

    /** Quita un equipo que se escaneó por error. */
    @Transactional
    public Map<String, Object> quitarUnidad(Integer idinventario, String imei, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());
        if (a.getEstado() != ESTADO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se quita un equipo mientras se está contando.");
        String id = imei == null ? "" : imei.trim().toUpperCase();
        InventarioAuditoriaUnidad u = unidadRepository.findByInventarioAuditoria_IdinventarioAndImei(idinventario, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El equipo '" + imei + "' no está en este conteo."));
        InventarioAuditoriaDetalle d = detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(idinventario, claveEquipo(u.getProductoMaster()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No se encontró el artículo de ese equipo en el conteo."));
        unidadRepository.delete(u);
        d.setConteo((short) Math.max(0, d.getConteo() - 1));
        return toMapDetalle(detalleRepository.save(d), u.getProductoMaster().getNombreBase());
    }

    /**
     * El encargado termina de contar y lo envía a revisión: se completa el resumen (los artículos de las secciones contadas
     * que no se escanearon quedan en 0) y se avisa a los administradores. Las existencias NO cambian.
     */
    @Transactional
    public Map<String, Object> cerrarConteo(Integer idinventario, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        validarAcceso(usuarioPorUsername(username), a.getTienda().getCodti());
        if (a.getEstado() != ESTADO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este inventario no está en fase de conteo.");

        List<InventarioAuditoriaDetalle> filas = new ArrayList<>(
                detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(idinventario));
        Set<Short> alcance = alcanceDe(a);
        if (!alcance.isEmpty()) agregarFaltantesDelAlcance(a, alcance, filas);
        // En las secciones que se contaron, lo que no se escaneó es lo que no se encontró
        Set<String> yaContados = filas.stream().map(InventarioAuditoriaDetalle::getCodpro).collect(Collectors.toSet());
        Map<Short, Seccion> secciones = new LinkedHashMap<>();
        for (InventarioAuditoriaDetalle d : filas) {
            if (d.getSeccion() != null) secciones.putIfAbsent(d.getSeccion().getIdseccion(), d.getSeccion());
        }
        for (Seccion s : alcance.isEmpty() ? secciones.values() : List.<Seccion>of()) {
            for (Producto p : productoRepository.findByTienda_CodtiAndSeccion_IdseccionAndActivoTrue(a.getTienda().getCodti(), s.getIdseccion())) {
                if (yaContados.contains(p.getCodpro())) continue;
                if (p.getProductoMaster() != null && p.getProductoMaster().getTipo() == TipoProducto.SERVICIO) continue;
                if (stockComoShort(p.getStock()) == 0) continue;
                filas.add(detalleRepository.save(InventarioAuditoriaDetalle.builder()
                        .inventarioAuditoria(a).seccion(s).codpro(p.getCodpro()).conteo((short) 0)
                        .stockSistema(stockComoShort(p.getStock())).estado(DETALLE_PENDIENTE).build()));
            }
        }
        a.setTotalFaltantes((int) filas.stream().filter(d -> diferenciaDe(d) < 0).count());
        a.setTotalSobrantes((int) filas.stream().filter(d -> diferenciaDe(d) > 0).count());
        a.setEstado(ESTADO_EN_REVISION);
        a = auditoriaRepository.save(a);
        notificacionService.notificarRol(Rol.ADMIN, "INVENTARIO_REVISION",
                "El inventario de " + a.getTienda().getNombre() + " está listo para revisar (" + a.getTotalFaltantes() + " faltante(s), "
                        + a.getTotalSobrantes() + " sobrante(s))", "#/inventario-fisico");
        return toMap(a, filas);
    }

    // ── Revisar y confirmar (administrador) ──────────────────────────────────

    @Transactional
    public Map<String, Object> resolverDetalle(Integer idinventario, Integer iddetalleInv, String accion, String motivo, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        Usuario usuario = usuarioPorUsername(username);
        exigirConfirmar(usuario);
        if (a.getEstado() != ESTADO_EN_REVISION)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se deciden las diferencias de un inventario que está en revisión.");

        InventarioAuditoriaDetalle d = detalleDe(idinventario, iddetalleInv);
        int diferencia = diferenciaDe(d);
        if (diferencia == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este artículo no tuvo diferencia; no hay nada que decidir.");

        if ("AJUSTAR".equalsIgnoreCase(accion)) {
            if (d.getProductoMaster() != null && diferencia > 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un equipo que sobra no se da de alta solo (no se sabe su costo ni su precio): justifícalo o regístralo en Equipos.");
            d.setEstado(DETALLE_AJUSTADO);
            d.setMotivo(null);
        } else if ("JUSTIFICAR".equalsIgnoreCase(accion)) {
            if (motivo == null || motivo.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo para justificar la diferencia sin ajustar las existencias.");
            d.setEstado(DETALLE_JUSTIFICADO);
            d.setMotivo(motivo.trim().length() > 255 ? motivo.trim().substring(0, 255) : motivo.trim());
        } else if ("PENDIENTE".equalsIgnoreCase(accion)) {
            d.setEstado(DETALLE_PENDIENTE);
            d.setMotivo(null);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acción no válida. Use AJUSTAR, JUSTIFICAR o PENDIENTE.");
        }
        return toMapDetalle(detalleRepository.save(d), null);
    }

    /**
     * El administrador corrige la cantidad contada de un accesorio al revisar, sin regresar el conteo: pide el motivo y queda
     * guardado lo que había contado el encargado, quién lo corrigió y por qué. Su decisión anterior sobre ese artículo se borra.
     */
    @Transactional
    public Map<String, Object> corregirConteoRevision(Integer idinventario, Integer iddetalleInv, Integer conteo, String motivo, String username) {
        InventarioAuditoria a = enRevisionParaCorregir(idinventario, username);
        InventarioAuditoriaDetalle d = detalleDe(idinventario, iddetalleInv);
        if (d.getProductoMaster() != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los equipos se corrigen por IMEI: marca cuál sí estaba o cuál no.");
        if (conteo == null || conteo < 0 || conteo > Short.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El conteo debe ser un número entre 0 y " + Short.MAX_VALUE + ".");
        if (conteo.shortValue() == d.getConteo())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esa ya es la cantidad contada.");
        anotarCorreccion(d, motivo, username);
        d.setConteo(conteo.shortValue());
        d = detalleRepository.save(d);
        recalcularTotales(a);
        return toMapDetalle(d, null);
    }

    /** Lo mismo para un equipo: marca que un IMEI sí estaba (de los que no se encontraron) o que no estaba (de los contados). */
    @Transactional
    public Map<String, Object> corregirEquipoRevision(Integer idinventario, String imei, boolean encontrado, String motivo, String username) {
        InventarioAuditoria a = enRevisionParaCorregir(idinventario, username);
        String id = imei == null ? "" : imei.replaceAll("[\\s-]", "").toUpperCase();
        InventarioAuditoriaDetalle d;
        if (encontrado) {
            ProductoImei u = imeiRepository.findByImei(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El IMEI '" + imei + "' no existe."));
            if (unidadRepository.findByInventarioAuditoria_IdinventarioAndImei(idinventario, u.getImei()).isPresent())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese equipo ya está contado.");
            boolean enSistema = ProductoImei.DISPONIBLE.equals(u.getEstado()) && u.getTienda() != null
                    && u.getTienda().getCodti().equals(a.getTienda().getCodti());
            if (!enSistema)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo se marca como encontrado un equipo que el sistema tiene disponible en esta sucursal.");
            d = detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(idinventario, claveEquipo(u.getProductoMaster()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Ese equipo no forma parte de lo que cubre este inventario."));
            anotarCorreccion(d, motivo, username);
            unidadRepository.save(InventarioAuditoriaUnidad.builder()
                    .inventarioAuditoria(a).productoMaster(u.getProductoMaster()).imei(u.getImei()).enSistema(true).build());
            d.setConteo((short) (d.getConteo() + 1));
        } else {
            InventarioAuditoriaUnidad u = unidadRepository.findByInventarioAuditoria_IdinventarioAndImei(idinventario, id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El equipo '" + imei + "' no está en este conteo."));
            d = detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(idinventario, claveEquipo(u.getProductoMaster()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No se encontró el artículo de ese equipo en el conteo."));
            anotarCorreccion(d, motivo, username);
            unidadRepository.delete(u);
            d.setConteo((short) Math.max(0, d.getConteo() - 1));
        }
        d = detalleRepository.save(d);
        recalcularTotales(a);
        return toMapDetalle(d, d.getProductoMaster().getNombreBase());
    }

    /** Los totales de faltantes y sobrantes que ve quien revisa se vuelven a contar después de corregir. */
    private void recalcularTotales(InventarioAuditoria a) {
        List<InventarioAuditoriaDetalle> filas = detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(a.getIdinventario());
        a.setTotalFaltantes((int) filas.stream().filter(x -> diferenciaDe(x) < 0).count());
        a.setTotalSobrantes((int) filas.stream().filter(x -> diferenciaDe(x) > 0).count());
        auditoriaRepository.save(a);
    }

    private InventarioAuditoria enRevisionParaCorregir(Integer idinventario, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        exigirConfirmar(usuarioPorUsername(username));
        if (a.getEstado() != ESTADO_EN_REVISION)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se corrige el conteo de un inventario que está en revisión.");
        return a;
    }

    /** Guarda lo que había contado el encargado (solo la primera vez), el motivo y quién corrigió; la decisión previa se borra. */
    private void anotarCorreccion(InventarioAuditoriaDetalle d, String motivo, String username) {
        if (motivo == null || motivo.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo de la corrección.");
        if (d.getConteoOriginal() == null) d.setConteoOriginal(d.getConteo());
        String m = motivo.trim();
        String previo = d.getCorreccionMotivo();
        String junto = previo == null || previo.isBlank() ? m : previo + " | " + m;
        d.setCorreccionMotivo(junto.length() > 255 ? junto.substring(junto.length() - 255) : junto);
        d.setCorreccionPor(username);
        d.setEstado(DETALLE_PENDIENTE);
        d.setMotivo(null);
    }

    /** Decide «ajustar» en todas las diferencias que siguen pendientes (menos los equipos que sobran, que no se pueden dar de alta solos). */
    @Transactional
    public Map<String, Object> ajustarTodo(Integer idinventario, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        exigirConfirmar(usuarioPorUsername(username));
        if (a.getEstado() != ESTADO_EN_REVISION)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se deciden las diferencias de un inventario que está en revisión.");
        int ajustadas = 0, sinDecidir = 0;
        for (InventarioAuditoriaDetalle d : detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(idinventario)) {
            int dif = diferenciaDe(d);
            if (dif == 0 || d.getEstado() != DETALLE_PENDIENTE) continue;
            if (d.getProductoMaster() != null && dif > 0) { sinDecidir++; continue; }
            d.setEstado(DETALLE_AJUSTADO);
            detalleRepository.save(d);
            ajustadas++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ajustadas", ajustadas);
        m.put("sinDecidir", sinDecidir);
        return m;
    }

    /** El administrador regresa el conteo al encargado para que lo recuente: las decisiones se borran y se vuelve a contar. */
    @Transactional
    public Map<String, Object> devolver(Integer idinventario, String comentario, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        exigirConfirmar(usuarioPorUsername(username));
        if (a.getEstado() != ESTADO_EN_REVISION)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se devuelve un inventario que está en revisión.");
        List<InventarioAuditoriaDetalle> filas = detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(idinventario);
        for (InventarioAuditoriaDetalle d : filas) {
            d.setEstado(DETALLE_PENDIENTE);
            d.setMotivo(null);
            detalleRepository.save(d);
        }
        a.setEstado(ESTADO_ABIERTO);
        a = auditoriaRepository.save(a);
        notificacionService.notificarTienda(a.getTienda().getCodti(), "INVENTARIO_DEVUELTO",
                "El administrador pidió recontar el inventario" + (comentario != null && !comentario.isBlank() ? ": " + comentario.trim() : ""),
                "#/inventario-fisico");
        return toMap(a, filas);
    }

    /** Cierra el inventario: las diferencias decididas como «ajustar» se aplican a las existencias. */
    @Transactional
    public Map<String, Object> finalizar(Integer idinventario, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        Usuario usuario = usuarioPorUsername(username);
        exigirConfirmar(usuario);
        if (a.getEstado() != ESTADO_EN_REVISION)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se cierra un inventario que ya se envió a revisión.");

        List<InventarioAuditoriaDetalle> filas = detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(idinventario);
        long pendientes = filas.stream().filter(d -> diferenciaDe(d) != 0 && d.getEstado() == DETALLE_PENDIENTE).count();
        if (pendientes > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aún hay " + pendientes + " diferencia(s) sin decidir (ajustar o justificar).");

        String ref = "Inventario físico #" + idinventario;
        for (InventarioAuditoriaDetalle d : filas) {
            int dif = diferenciaDe(d);
            if (dif == 0 || d.getEstado() != DETALLE_AJUSTADO) continue;
            if (d.getProductoMaster() != null) darDeBajaFaltantes(a, d, -dif, ref);
            else ajustarExistencia(a, d, dif, ref);
        }

        a.setTotalFaltantes((int) filas.stream().filter(d -> diferenciaDe(d) < 0).count());
        a.setTotalSobrantes((int) filas.stream().filter(d -> diferenciaDe(d) > 0).count());
        a.setEstado(ESTADO_FINALIZADO);
        a.setFechaFin(LocalDateTime.now());
        a.setUsuarioConfirma(usuario);
        a = auditoriaRepository.save(a);
        notificacionService.notificarTienda(a.getTienda().getCodti(), "INVENTARIO_CERRADO",
                "Se cerró el inventario de " + a.getTienda().getNombre() + ": las existencias ya quedaron ajustadas", "#/inventario-fisico");
        return toMap(a, filas);
    }

    /** Suma a la existencia lo que se encontró de más (o resta lo que faltó) — la diferencia medida, no el conteo, para respetar lo vendido mientras tanto. */
    private void ajustarExistencia(InventarioAuditoria a, InventarioAuditoriaDetalle d, int diferencia, String ref) {
        Producto p = productoRepository.findByCodproAndTienda_Codti(d.getCodpro(), a.getTienda().getCodti())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El artículo '" + d.getCodpro() + "' ya no existe en esta sucursal."));
        BigDecimal antes = p.getStock() != null ? p.getStock() : BigDecimal.ZERO;
        p.setStock(antes.add(BigDecimal.valueOf(diferencia)));
        productoRepository.save(p);
        movimientoInventarioService.registrar(p, antes, "AJUSTE", "Ajuste por inventario físico #" + a.getIdinventario(), ref);
    }

    /** Los equipos disponibles que no se encontraron se dan de baja. */
    private void darDeBajaFaltantes(InventarioAuditoria a, InventarioAuditoriaDetalle d, int cuantos, String ref) {
        Set<String> contados = unidadRepository.findByInventarioAuditoria_IdinventarioOrderByIdAsc(a.getIdinventario()).stream()
                .map(InventarioAuditoriaUnidad::getImei).collect(Collectors.toSet());
        int hechas = 0;
        for (ProductoImei u : imeiRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(
                d.getProductoMaster().getIdprodmaster(), a.getTienda().getCodti(), ProductoImei.DISPONIBLE)) {
            if (hechas >= cuantos) break;
            if (contados.contains(u.getImei())) continue;
            u.setEstado("DEBAJA");
            imeiRepository.save(u);
            movimientoInventarioService.registrarEquipo(u, -1, "AJUSTE", "No se encontró en el inventario físico #" + a.getIdinventario(), ref);
            hechas++;
        }
    }

    /** Cancela un inventario que no se terminó (el encargado, mientras cuenta; en revisión, solo quien lo puede confirmar). */
    @Transactional
    public Map<String, Object> cancelar(Integer idinventario, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        Usuario usuario = usuarioPorUsername(username);
        validarAcceso(usuario, a.getTienda().getCodti());
        if (a.getEstado() == ESTADO_EN_REVISION) exigirConfirmar(usuario);
        else if (a.getEstado() != ESTADO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este inventario ya no se puede cancelar.");
        a.setEstado(ESTADO_CANCELADO);
        a.setFechaFin(LocalDateTime.now());
        return toMap(auditoriaRepository.save(a), List.of());
    }

    // ── Consultar ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Integer idinventario, boolean soloDiferencias, String username) {
        InventarioAuditoria a = auditoriaDe(idinventario);
        Usuario usuario = usuarioPorUsername(username);
        validarAcceso(usuario, a.getTienda().getCodti());
        boolean verCostos = permisoService.tiene(usuario.getRol(), Permiso.COSTOS_VER.name());
        Integer codti = a.getTienda().getCodti();

        List<InventarioAuditoriaDetalle> filas = detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(idinventario);
        Map<String, Producto> productos = productoRepository.findByCodproInAndTienda_Codti(
                        filas.stream().filter(d -> d.getProductoMaster() == null).map(InventarioAuditoriaDetalle::getCodpro).distinct().collect(Collectors.toList()), codti)
                .stream().collect(Collectors.toMap(Producto::getCodpro, p -> p, (x, y) -> x));
        List<InventarioAuditoriaUnidad> contadas = unidadRepository.findByInventarioAuditoria_IdinventarioOrderByIdAsc(idinventario);
        boolean cerrado = a.getEstado() != ESTADO_ABIERTO;

        BigDecimal valorFaltantes = BigDecimal.ZERO, valorSobrantes = BigDecimal.ZERO;
        List<Map<String, Object>> items = new ArrayList<>();
        for (InventarioAuditoriaDetalle d : filas) {
            int dif = diferenciaDe(d);
            Map<String, Object> m;
            BigDecimal valor = BigDecimal.ZERO;
            if (d.getProductoMaster() != null) {
                m = toMapDetalle(d, d.getProductoMaster().getNombreBase());
                m.put("codpro", null);
                m.put("esEquipo", true);
                List<Map<String, Object>> imeis = new ArrayList<>();
                Set<String> escaneados = new HashSet<>();
                for (InventarioAuditoriaUnidad u : contadas) {
                    if (!u.getProductoMaster().getIdprodmaster().equals(d.getProductoMaster().getIdprodmaster())) continue;
                    escaneados.add(u.getImei());
                    Map<String, Object> um = new LinkedHashMap<>();
                    um.put("imei", u.getImei());
                    um.put("enSistema", u.getEnSistema());
                    imeis.add(um);
                    if (!Boolean.TRUE.equals(u.getEnSistema())) {
                        valor = valor.add(imeiRepository.findByImei(u.getImei()).map(ProductoImei::getCosto).filter(Objects::nonNull).orElse(BigDecimal.ZERO));
                    }
                }
                m.put("imeis", imeis);
                if (cerrado && a.getEstado() != ESTADO_CANCELADO) {
                    List<Map<String, Object>> faltan = new ArrayList<>();
                    BigDecimal valorFalta = BigDecimal.ZERO;
                    for (ProductoImei u : imeiRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(
                            d.getProductoMaster().getIdprodmaster(), codti, ProductoImei.DISPONIBLE)) {
                        if (escaneados.contains(u.getImei())) continue;
                        Map<String, Object> fm = new LinkedHashMap<>();
                        fm.put("imei", u.getImei());
                        faltan.add(fm);
                        valorFalta = valorFalta.add(u.getCosto() != null ? u.getCosto() : BigDecimal.ZERO);
                    }
                    m.put("faltantesImeis", faltan);
                    valor = valor.subtract(valorFalta);
                }
            } else {
                Producto p = productos.get(d.getCodpro());
                m = toMapDetalle(d, p != null && p.getProductoMaster() != null ? p.getProductoMaster().getNombreBase() : d.getCodpro());
                m.put("esEquipo", false);
                BigDecimal costo = p != null && p.getPreciopro() != null ? p.getPreciopro() : BigDecimal.ZERO;
                m.put("costoUnitario", verCostos ? costo : null);
                valor = costo.multiply(BigDecimal.valueOf(dif));
            }
            m.put("motivo", d.getMotivo());
            m.put("valorDiferencia", verCostos ? valor : null);
            if (valor.signum() < 0) valorFaltantes = valorFaltantes.add(valor);
            if (valor.signum() > 0) valorSobrantes = valorSobrantes.add(valor);
            if (!soloDiferencias || dif != 0) items.add(m);
        }

        Map<String, Object> resultado = new LinkedHashMap<>(toMap(a, filas));
        resultado.put("valorFaltantes", verCostos ? valorFaltantes : null);
        resultado.put("valorSobrantes", verCostos ? valorSobrantes : null);
        resultado.put("items", items);
        return resultado;
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

    // ── Alcance (qué categorías cubre el inventario) ─────────────────────────

    private String alcanceDeCategorias(List<Short> categorias) {
        if (categorias == null || categorias.isEmpty()) return null;
        List<Short> ids = categorias.stream().filter(Objects::nonNull).distinct().toList();
        for (Short id : ids) {
            Categoria c = categoriaRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría " + id + " no existe."));
            if (c.ruta().get(0).getTipo() == TipoProducto.SERVICIO)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los servicios no se inventarían: no tienen existencias que contar.");
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static Set<Short> alcanceDe(InventarioAuditoria a) {
        if (a.getAlcance() == null || a.getAlcance().isBlank()) return Set.of();
        return Arrays.stream(a.getAlcance().split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Short::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** ¿El artículo está en alguna de esas categorías (o en una subcategoría de ellas)? */
    private static boolean enAlcance(ProductoMaster m, Set<Short> alcance) {
        if (m == null || m.getCategoria() == null) return false;
        return m.getCategoria().ruta().stream().anyMatch(c -> alcance.contains(c.getIdcat()));
    }

    private String nombresDelAlcance(Set<Short> alcance) {
        return alcance.stream().map(id -> categoriaRepository.findById(id).map(Categoria::getNombre).orElse("#" + id))
                .collect(Collectors.joining(", "));
    }

    /**
     * Con alcance, lo que está en el sistema dentro de esas categorías y no se contó queda en 0: así un inventario «solo de micas» o
     * «solo de equipos» marca como faltante lo que no apareció, sin tocar el resto de la tienda.
     */
    private void agregarFaltantesDelAlcance(InventarioAuditoria a, Set<Short> alcance, List<InventarioAuditoriaDetalle> filas) {
        Integer codti = a.getTienda().getCodti();
        Set<String> yaContados = filas.stream().map(InventarioAuditoriaDetalle::getCodpro).collect(Collectors.toSet());
        for (Producto p : productoRepository.findByTienda_CodtiAndActivoTrue(codti)) {
            if (yaContados.contains(p.getCodpro())) continue;
            ProductoMaster m = p.getProductoMaster();
            if (m == null || m.getTipo() == TipoProducto.SERVICIO || m.getTipo() == TipoProducto.CELULAR || m.getTipo() == TipoProducto.TABLET) continue;
            if (!enAlcance(m, alcance) || stockComoShort(p.getStock()) == 0) continue;
            filas.add(detalleRepository.save(InventarioAuditoriaDetalle.builder()
                    .inventarioAuditoria(a).seccion(p.getSeccion()).codpro(p.getCodpro()).conteo((short) 0)
                    .stockSistema(stockComoShort(p.getStock())).estado(DETALLE_PENDIENTE).build()));
        }
        Map<Integer, List<ProductoImei>> porArticulo = new LinkedHashMap<>();
        for (ProductoImei u : imeiRepository.deLaSucursal(codti, List.of(ProductoImei.DISPONIBLE))) {
            porArticulo.computeIfAbsent(u.getProductoMaster().getIdprodmaster(), k -> new ArrayList<>()).add(u);
        }
        for (List<ProductoImei> unidades : porArticulo.values()) {
            ProductoMaster m = unidades.get(0).getProductoMaster();
            if (yaContados.contains(claveEquipo(m)) || !enAlcance(m, alcance)) continue;
            filas.add(detalleRepository.save(InventarioAuditoriaDetalle.builder()
                    .inventarioAuditoria(a).productoMaster(m).codpro(claveEquipo(m)).conteo((short) 0)
                    .stockSistema((short) unidades.size()).estado(DETALLE_PENDIENTE).build()));
        }
    }

    private static String claveEquipo(ProductoMaster m) {
        return "EQ" + m.getIdprodmaster();
    }

    private InventarioAuditoria auditoriaDe(Integer idinventario) {
        return auditoriaRepository.findById(idinventario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventario no encontrado: " + idinventario));
    }

    private InventarioAuditoriaDetalle detalleDe(Integer idinventario, Integer iddetalleInv) {
        InventarioAuditoriaDetalle d = detalleRepository.findById(iddetalleInv)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detalle no encontrado."));
        if (!d.getInventarioAuditoria().getIdinventario().equals(idinventario))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese detalle no pertenece a este inventario.");
        return d;
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

    /** Decidir qué se ajusta y cerrar el inventario es de quien puede confirmarlo (por omisión, el administrador). */
    private void exigirConfirmar(Usuario usuario) {
        if (!permisoService.tiene(usuario.getRol(), Permiso.INVENTARIO_CONFIRMAR.name()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo quien confirma el inventario (administrador) decide los ajustes y lo cierra.");
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
            case ESTADO_ABIERTO -> "Contando";
            case ESTADO_EN_REVISION -> "En revisión";
            case ESTADO_FINALIZADO -> "Cerrado";
            case ESTADO_CANCELADO -> "Cancelado";
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
        m.put("nombreConfirma", a.getUsuarioConfirma() != null ? a.getUsuarioConfirma().getNombreCompleto() : null);
        m.put("estado", a.getEstado());
        m.put("estadoDisplay", nombreEstado(a.getEstado()));
        m.put("fechaInicio", a.getFechaInicio());
        m.put("fechaFin", a.getFechaFin());
        Set<Short> alcance = alcanceDe(a);
        m.put("alcance", alcance.isEmpty() ? "" : nombresDelAlcance(alcance));
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
        // Conteo ciego: mientras se cuenta, quien cuenta no recibe lo que dice el sistema ni la diferencia (no puede cuadrar el conteo)
        boolean contando = d.getInventarioAuditoria() != null && d.getInventarioAuditoria().getEstado() != null
                && d.getInventarioAuditoria().getEstado() == ESTADO_ABIERTO;
        m.put("stockSistema", contando ? null : d.getStockSistema());
        m.put("diferencia", contando ? null : diferenciaDe(d));
        m.put("estado", d.getEstado());
        if (d.getConteoOriginal() != null && !contando) {
            m.put("conteoOriginal", d.getConteoOriginal());
            m.put("correccionMotivo", d.getCorreccionMotivo());
            m.put("correccionPor", d.getCorreccionPor());
        }
        return m;
    }
}
