package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ProductoRequestDTO;
import com.skycel.backend.domain.dto.request.UnidadEquipoDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Permiso;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.equipo.*;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Los equipos (celulares y tablets): cada unidad se identifica por su IMEI (o, en tablets, su número de serie) y apunta
 * directo al artículo al que pertenece y a la sucursal donde está; no hay un código por modelo. Las existencias de un
 * artículo en una sucursal son sus unidades DISPONIBLES.
 */
@Service
@RequiredArgsConstructor
public class EquipoService {

    private static final Pattern IMEI_15 = Pattern.compile("\\d{15}");
    private static final Pattern SERIE = Pattern.compile("[A-Za-z0-9]{5,20}");
    private static final Set<String> CONDICIONES = Set.of("NUEVO", "SEMINUEVO", "REACONDICIONADO");
    /** Estados que se muestran en el inventario: lo que está en la tienda o apartado en garantía. */
    private static final List<String> EN_INVENTARIO = List.of(ProductoImei.DISPONIBLE, ProductoImei.EN_GARANTIA);

    private final ProductoImeiRepository imeiRepository;
    private final ProductoMasterRepository masterRepository;
    private final TiendaRepository tiendaRepository;
    private final ProveedorRepository proveedorRepository;
    private final SeccionRepository seccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoService productoService;
    private final DescuentoService descuentoService;
    private final MovimientoInventarioService movimientoInventarioService;
    private final PermisoService permisoService;

    // ── Validación del IMEI / serie ──────────────────────────────────────────

    /** El tipo de equipo (celular o tablet) acepta cierto identificador: el celular, solo IMEI; la tablet, IMEI o serie. */
    static String normalizarIdentificador(String crudo, TipoProducto tipo) {
        String id = crudo == null ? "" : crudo.trim().replaceAll("[\\s-]", "").toUpperCase();
        if (tipo == TipoProducto.CELULAR) {
            if (!IMEI_15.matcher(id).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + crudo + "' no es un IMEI: un celular se registra por su IMEI, que tiene 15 dígitos.");
            }
        } else if (!IMEI_15.matcher(id).matches() && !SERIE.matcher(id).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + crudo + "' no es válido: una tablet se registra por su IMEI (15 dígitos) o por su número de serie (5 a 20 letras o números).");
        }
        return id;
    }

    static String tipoDeIdentificador(String identificador) {
        return IMEI_15.matcher(identificador).matches() ? ProductoImei.IMEI : ProductoImei.SERIE;
    }

    static boolean esEquipo(TipoProducto tipo) {
        return tipo == TipoProducto.CELULAR || tipo == TipoProducto.TABLET;
    }

    private static String normalizarCondicion(String condicion) {
        if (condicion == null || condicion.isBlank()) return "NUEVO";
        String c = condicion.trim().toUpperCase();
        if (c.equals("USADO")) c = "SEMINUEVO";   // así se llamaba antes
        if (!CONDICIONES.contains(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Condición inválida: '" + condicion + "'. Use NUEVO, SEMINUEVO o REACONDICIONADO.");
        }
        return c;
    }

    // ── Alta ─────────────────────────────────────────────────────────────────

    @Transactional
    public List<EquipoResponseDto> registrar(EquipoRequestDto dto, String username) {
        Usuario usuario = usuario(username);
        Tienda tienda = tiendaDe(dto.getCodti(), usuario);
        exigirOperaTienda(usuario, tienda.getCodti());

        // Registrar un artículo nuevo (categoría + nombre) es de quien puede editar el catálogo; sumar unidades a uno existente, de quien ajusta existencias
        if (dto.getIdProductoMaster() == null && !permisoService.tiene(usuario.getRol(), Permiso.INVENTARIO_EDITAR.name())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien puede editar artículos registra un artículo nuevo.");
        }
        ProductoRequestDTO articulo = new ProductoRequestDTO();
        articulo.setIdProductoMaster(dto.getIdProductoMaster() != null ? dto.getIdProductoMaster().longValue() : null);
        articulo.setIdCategoria(dto.getIdCategoria());
        articulo.setNombreProducto(dto.getNombreProducto());
        articulo.setIdDescripcion(dto.getIdDescripcion());
        articulo.setIdColor(dto.getIdColor());
        articulo.setDiasGarantia(dto.getDiasGarantia());
        ProductoMaster master = productoService.resolverMaster(articulo);
        if (!esEquipo(master.getTipo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + master.getNombreBase() + "' no es un celular ni una tablet: los accesorios y servicios se registran como producto, con su código.");
        }

        if (dto.getUnidades() == null || dto.getUnidades().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el IMEI (o serie, en tablets) de al menos un equipo.");
        }
        Proveedor proveedor = dto.getIdProveedor() == null ? null : proveedorRepository.findById(dto.getIdProveedor())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado: " + dto.getIdProveedor()));
        Seccion seccion = dto.getIdSeccion() == null ? null : seccionRepository.findById(dto.getIdSeccion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sección no encontrada: " + dto.getIdSeccion()));

        List<ProductoImei> nuevas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (UnidadEquipoDTO u : dto.getUnidades()) {
            String id = normalizarIdentificador(u.getImei(), master.getTipo());
            if (!vistos.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador '" + id + "' viene repetido en la lista.");
            }
            if (imeiRepository.existsByImei(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El identificador '" + id + "' ya está registrado en el sistema.");
            }
            BigDecimal precio = u.getPrecioVenta() != null ? u.getPrecioVenta() : dto.getPrecioVenta();
            if (precio == null || precio.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el precio de venta de '" + id + "' (debe ser mayor a $0).");
            }
            BigDecimal costo = u.getCostoUnitario() != null ? u.getCostoUnitario() : dto.getCosto();
            if (costo == null || costo.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el costo de '" + id + "' (puede ser $0, no negativo).");
            }
            nuevas.add(ProductoImei.builder()
                    .productoMaster(master).tienda(tienda).imei(id).tipoIdentificador(tipoDeIdentificador(id))
                    .estado(ProductoImei.DISPONIBLE).condicion(normalizarCondicion(u.getCondicion()))
                    .precioVenta(precio).costo(costo).proveedor(proveedor).seccion(seccion).rezagado(false)
                    .build());
        }
        if (dto.getStockMinimo() != null) {
            if (dto.getStockMinimo() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El stock mínimo no puede ser negativo.");
            master.setStockMinimoEquipos(dto.getStockMinimo());
            masterRepository.save(master);
        }

        String motivo = dto.getMotivo() != null && !dto.getMotivo().isBlank() ? dto.getMotivo() : "Alta de equipo";
        boolean verCostos = verCostos(usuario);
        List<EquipoResponseDto> respuesta = new ArrayList<>();
        for (ProductoImei u : nuevas) {
            ProductoImei guardada = imeiRepository.save(u);
            movimientoInventarioService.registrarEquipo(guardada, +1, "ALTA", motivo, null);
            respuesta.add(toDto(guardada, verCostos));
        }
        return respuesta;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    /** Las unidades que hay en una sucursal (por omisión las disponibles y las que están en garantía). */
    @Transactional(readOnly = true)
    public List<EquipoResponseDto> listar(Integer codti, Collection<String> estados, String username) {
        Usuario usuario = usuario(username);
        boolean verCostos = verCostos(usuario);
        Collection<String> en = estados == null || estados.isEmpty() ? EN_INVENTARIO : estados;
        return imeiRepository.deLaSucursal(codti, en).stream().map(u -> toDto(u, verCostos)).collect(Collectors.toList());
    }

    /** Los artículos de una sucursal con cuántas unidades hay de cada uno (y los que están por debajo de su mínimo, aunque no quede ninguna). */
    @Transactional(readOnly = true)
    public List<EquipoArticuloDto> articulos(Integer codti, boolean conUnidades, String username) {
        boolean verCostos = verCostos(usuario(username));
        Tienda tienda = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sucursal no encontrada: " + codti));
        Map<Integer, List<ProductoImei>> porArticulo = imeiRepository.deLaSucursal(codti, EN_INVENTARIO).stream()
                .collect(Collectors.groupingBy(u -> u.getProductoMaster().getIdprodmaster(), LinkedHashMap::new, Collectors.toList()));
        Map<Integer, ProductoMaster> masters = new LinkedHashMap<>();
        porArticulo.forEach((id, lista) -> masters.put(id, lista.get(0).getProductoMaster()));
        // artículos con mínimo definido que se quedaron sin unidades aquí
        for (ProductoMaster m : masterRepository.findByTipoInAndStockMinimoEquiposGreaterThanAndActivoTrue(
                List.of(TipoProducto.CELULAR, TipoProducto.TABLET), 0)) {
            masters.putIfAbsent(m.getIdprodmaster(), m);
        }
        List<EquipoArticuloDto> lista = new ArrayList<>();
        for (ProductoMaster m : masters.values()) {
            List<ProductoImei> unidades = porArticulo.getOrDefault(m.getIdprodmaster(), List.of());
            List<ProductoImei> disponibles = unidades.stream().filter(u -> ProductoImei.DISPONIBLE.equals(u.getEstado())).collect(Collectors.toList());
            BigDecimal min = disponibles.stream().map(ProductoImei::getPrecioVenta).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            BigDecimal max = disponibles.stream().map(ProductoImei::getPrecioVenta).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            int minimo = m.getStockMinimoEquipos() != null ? m.getStockMinimoEquipos() : 0;
            EquipoArticuloDto.EquipoArticuloDtoBuilder b = EquipoArticuloDto.builder()
                    .idprodmaster(m.getIdprodmaster()).nombreArticulo(m.getNombreBase()).tipo(m.getTipo().name())
                    .codti(tienda.getCodti()).nombreTienda(tienda.getNombre())
                    .disponibles(disponibles.size()).enGarantia(unidades.size() - disponibles.size())
                    .precioMinimo(min).precioMaximo(max).stockMinimo(minimo)
                    .bajoStock(minimo > 0 && disponibles.size() <= minimo);
            rellenarCategoria(m, b);
            if (m.getColor() != null) b.colorArticulo(m.getColor().getNombre());
            if (conUnidades) b.unidades(disponibles.stream().map(u -> toDto(u, verCostos)).collect(Collectors.toList()));
            lista.add(b.build());
        }
        return lista;
    }

    /** Todos los artículos de equipos (celulares y tablets) del sistema, para elegir de cuál se dan de alta unidades. */
    @Transactional(readOnly = true)
    public List<EquipoArticuloDto> catalogo() {
        return masterRepository.findByActivoTrue().stream()
                .filter(m -> esEquipo(m.getTipo()))
                .sorted(Comparator.comparing(ProductoMaster::getNombreBase, String.CASE_INSENSITIVE_ORDER))
                .map(m -> {
                    EquipoArticuloDto.EquipoArticuloDtoBuilder b = EquipoArticuloDto.builder()
                            .idprodmaster(m.getIdprodmaster()).nombreArticulo(m.getNombreBase()).tipo(m.getTipo().name())
                            .stockMinimo(m.getStockMinimoEquipos() != null ? m.getStockMinimoEquipos() : 0);
                    rellenarCategoria(m, b);
                    if (m.getColor() != null) b.colorArticulo(m.getColor().getNombre());
                    return b.build();
                }).collect(Collectors.toList());
    }

    /** Equipos disponibles de la sucursal cuyo IMEI/serie o artículo contiene el texto (el buscador del punto de venta). */
    @Transactional(readOnly = true)
    public List<EquipoResponseDto> buscarDisponibles(Integer codti, String texto, String username) {
        if (texto == null || texto.trim().length() < 2) return List.of();
        boolean verCostos = verCostos(usuario(username));
        return imeiRepository.buscarDisponibles(codti, texto.trim()).stream().limit(60)
                .map(u -> toDto(u, verCostos)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EquipoResponseDto porImei(String imei, String username) {
        ProductoImei u = imeiRepository.findByImei(imei == null ? "" : imei.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El IMEI o serie '" + imei + "' no está registrado."));
        return toDto(u, verCostos(usuario(username)));
    }

    // ── Cambios ──────────────────────────────────────────────────────────────

    /** Corrige cualquier dato de un equipo, incluido su IMEI o serie. */
    @Transactional
    public EquipoResponseDto editar(String imei, EquipoUpdateDto dto, String username) {
        Usuario usuario = usuario(username);
        ProductoImei u = buscar(imei);
        exigirOperaTienda(usuario, u.getTienda().getCodti());

        if (dto.getPrecioVenta() != null) {
            if (dto.getPrecioVenta().signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio debe ser mayor a $0.");
            u.setPrecioVenta(dto.getPrecioVenta());
        }
        if (dto.getCosto() != null) {
            if (dto.getCosto().signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo no puede ser negativo.");
            u.setCosto(dto.getCosto());
        }
        if (dto.getCondicion() != null) u.setCondicion(normalizarCondicion(dto.getCondicion()));
        if (dto.getIdProveedor() != null) {
            u.setProveedor(proveedorRepository.findById(dto.getIdProveedor())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado: " + dto.getIdProveedor())));
        }
        if (dto.getIdSeccion() != null) {
            u.setSeccion(seccionRepository.findById(dto.getIdSeccion())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sección no encontrada: " + dto.getIdSeccion())));
        }
        if (dto.getRezagado() != null) u.setRezagado(dto.getRezagado());

        boolean cambiaArticulo = dto.getIdProductoMaster() != null && !dto.getIdProductoMaster().equals(u.getProductoMaster().getIdprodmaster());
        boolean cambiaImei = dto.getNuevoImei() != null && !dto.getNuevoImei().isBlank();
        if (cambiaArticulo || cambiaImei) {
            // Una unidad vendida o en camino ya está en documentos (venta, traspaso): se corrige sus datos, no su identidad
            if (!List.of(ProductoImei.DISPONIBLE, "DEBAJA", "DEVUELTO").contains(u.getEstado())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se puede cambiar el IMEI ni el artículo de un equipo que está " + u.getEstado().toLowerCase().replace('_', ' ')
                                + ": ya figura en una venta, un traspaso o una garantía.");
            }
        }
        if (cambiaArticulo) {
            ProductoMaster nuevo = masterRepository.findById(dto.getIdProductoMaster())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artículo no encontrado: " + dto.getIdProductoMaster()));
            if (!esEquipo(nuevo.getTipo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'" + nuevo.getNombreBase() + "' no es un celular ni una tablet.");
            }
            u.setProductoMaster(nuevo);
        }
        if (cambiaImei) {
            String nuevoId = normalizarIdentificador(dto.getNuevoImei(), u.getProductoMaster().getTipo());
            if (!nuevoId.equals(u.getImei())) {
                if (imeiRepository.existsByImei(nuevoId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El identificador '" + nuevoId + "' ya está registrado en otro equipo.");
                }
                u.setImei(nuevoId);
                u.setTipoIdentificador(tipoDeIdentificador(nuevoId));
            }
        }
        return toDto(imeiRepository.save(u), verCostos(usuario));
    }

    /** Pone el mismo precio (y, si se pide, el mismo costo) a todas las unidades disponibles de un artículo en una sucursal. */
    @Transactional
    public int precioDelArticulo(Integer idprodmaster, Integer codti, PrecioArticuloDto dto, String username) {
        Usuario usuario = usuario(username);
        exigirOperaTienda(usuario, codti);
        if (dto.getPrecioVenta() == null || dto.getPrecioVenta().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio debe ser mayor a $0.");
        }
        if (dto.getCosto() != null && dto.getCosto().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo no puede ser negativo.");
        }
        List<ProductoImei> unidades = imeiRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(idprodmaster, codti, ProductoImei.DISPONIBLE);
        for (ProductoImei u : unidades) {
            u.setPrecioVenta(dto.getPrecioVenta());
            if (dto.getCosto() != null) u.setCosto(dto.getCosto());
        }
        imeiRepository.saveAll(unidades);
        return unidades.size();
    }

    @Transactional
    public void stockMinimo(Integer idprodmaster, int minimo) {
        if (minimo < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El stock mínimo no puede ser negativo.");
        ProductoMaster m = masterRepository.findById(idprodmaster)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado: " + idprodmaster));
        if (!esEquipo(m.getTipo())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este artículo no es un equipo.");
        m.setStockMinimoEquipos(minimo);
        masterRepository.save(m);
    }

    @Transactional
    public EquipoResponseDto marcarRezagado(String imei, boolean rezagado, String username) {
        Usuario usuario = usuario(username);
        ProductoImei u = buscar(imei);
        exigirOperaTienda(usuario, u.getTienda().getCodti());
        u.setRezagado(rezagado);
        return toDto(imeiRepository.save(u), verCostos(usuario));
    }

    /** Saca de las existencias un equipo que ya no está (perdido, dañado, robado). */
    @Transactional
    public EquipoResponseDto darDeBaja(String imei, String motivo, String username) {
        Usuario usuario = usuario(username);
        ProductoImei u = buscar(imei);
        exigirOperaTienda(usuario, u.getTienda().getCodti());
        if (!ProductoImei.DISPONIBLE.equals(u.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se da de baja un equipo disponible (estado actual: " + u.getEstado() + ").");
        }
        if (motivo == null || motivo.trim().length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escriba el motivo de la baja (por ejemplo: robo, daño, error de captura).");
        }
        u.setEstado("DEBAJA");
        imeiRepository.save(u);
        movimientoInventarioService.registrarEquipo(u, -1, "SALIDA", motivo, null);
        return toDto(u, verCostos(usuario));
    }

    // ── Piezas ───────────────────────────────────────────────────────────────

    private ProductoImei buscar(String imei) {
        return imeiRepository.findByImei(imei == null ? "" : imei.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El IMEI o serie '" + imei + "' no está registrado."));
    }

    EquipoResponseDto toDto(ProductoImei u, boolean verCostos) {
        ProductoMaster m = u.getProductoMaster();
        BigDecimal precio = u.getPrecioVenta() != null ? u.getPrecioVenta() : BigDecimal.ZERO;
        BigDecimal descuento = ProductoImei.DISPONIBLE.equals(u.getEstado())
                ? descuentoService.calcularDescuento(m, u.getTienda() != null ? u.getTienda().getCodti() : null, precio, LocalDateTime.now())
                : BigDecimal.ZERO;
        EquipoResponseDto.EquipoResponseDtoBuilder b = EquipoResponseDto.builder()
                .idUnidad(u.getId()).imei(u.getImei()).tipoIdentificador(u.getTipoIdentificador())
                .idprodmaster(m.getIdprodmaster()).nombreArticulo(m.getNombreBase()).tipo(m.getTipo().name())
                .nombreProducto(m.getNombreProducto()).diasGarantia(m.getDiasGarantia())
                .estado(u.getEstado()).condicion(u.getCondicion())
                .precioVenta(precio).descuentoAplicado(descuento).precioFinal(precio.subtract(descuento))
                .costo(verCostos ? u.getCosto() : null)
                .rezagado(Boolean.TRUE.equals(u.getRezagado())).fechaRegistro(u.getFechaRegistro())
                .diasEnExistencia(u.getFechaRegistro() != null ? ChronoUnit.DAYS.between(u.getFechaRegistro(), LocalDateTime.now()) : null);
        if (m.getColor() != null) b.colorArticulo(m.getColor().getNombre());
        if (u.getTienda() != null) b.codti(u.getTienda().getCodti()).nombreTienda(u.getTienda().getNombre());
        if (u.getSeccion() != null) b.idSeccion(u.getSeccion().getIdseccion()).nombreSeccion(u.getSeccion().getNombre());
        if (u.getProveedor() != null) {
            b.idProveedor(u.getProveedor().getIdproveedor());
            b.nombreProveedor(u.getProveedor().getNombreCorto() != null ? u.getProveedor().getNombreCorto() : u.getProveedor().getNombreFiscal());
        }
        if (m.getCategoria() != null) {
            Categoria cat = m.getCategoria();
            List<Categoria> ruta = cat.ruta();
            b.idCategoria(cat.getIdcat()).categoriaPrincipal(ruta.get(0).getNombre());
            if (ruta.size() >= 2) b.subcategoria1(ruta.get(1).getNombre());
            if (ruta.size() >= 3) b.subcategoria2(ruta.get(2).getNombre());
        }
        return b.build();
    }

    private void rellenarCategoria(ProductoMaster m, EquipoArticuloDto.EquipoArticuloDtoBuilder b) {
        if (m.getCategoria() == null) return;
        List<Categoria> ruta = m.getCategoria().ruta();
        b.idCategoria(m.getCategoria().getIdcat()).categoriaPrincipal(ruta.get(0).getNombre());
        if (ruta.size() >= 2) b.subcategoria1(ruta.get(1).getNombre());
        if (ruta.size() >= 3) b.subcategoria2(ruta.get(2).getNombre());
    }

    private boolean verCostos(Usuario usuario) {
        return permisoService.tiene(usuario.getRol(), Permiso.COSTOS_VER.name());
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private Tienda tiendaDe(Integer codti, Usuario usuario) {
        Integer id = codti != null ? codti : (usuario.getTienda() != null ? usuario.getTienda().getCodti() : null);
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la sucursal (codti).");
        return tiendaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada: " + id));
    }

    /** ROOT y ADMIN operan cualquier sucursal; los demás, solo la suya. */
    private void exigirOperaTienda(Usuario usuario, Integer codti) {
        if (usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN) return;
        if (usuario.getTienda() == null || !usuario.getTienda().getCodti().equals(codti)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede operar los equipos de su sucursal.");
        }
    }
}
