package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.*;
import com.skycel.backend.domain.dto.response.*;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.domain.mapper.ProductoMapper;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoMasterRepository productoMasterRepository;
    private final ProductoRepository       productoRepository;
    private final CategoriaRepository      categoriaRepository;
    private final TiendaRepository         tiendaRepository;
    private final ColorRepository          colorRepository;
    private final MagnitudRepository       magnitudRepository;
    private final ProveedorRepository      proveedorRepository;
    private final SeccionRepository        seccionRepository;
    private final ProductoImeiRepository   productoImeiRepository;
    private final CategoriaFolioRepository categoriaFolioRepository;
    private final ProductoMapper           productoMapper;
    private final MovimientoInventarioService movimientoInventarioService;

    /** Contador reservado para los equipos (CEL-000001...). categoria_folio no tiene FK real, así que 0 no choca con ninguna categoría. */
    private static final short FOLIO_EQUIPOS = 0;
    /** Condiciones admitidas de una unidad de equipo. */
    private static final Set<String> CONDICIONES = Set.of("NUEVO", "USADO", "REACONDICIONADO");

    // ──────────────────────────────────────────────────────────────────────────
    // PRODUCTO MASTER
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductoMasterResponseDTO> obtenerTodosMaster() {
        return productoMasterRepository.findByActivoTrue().stream()
                .map(productoMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductoMasterResponseDTO> buscarMaster(String query) {
        return productoMasterRepository.searchByKeyword(query).stream()
                .map(productoMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductoMasterResponseDTO crearMaster(ProductoMasterRequestDTO dto) {
        ProductoMaster master = productoMapper.toEntity(dto);
        Categoria cat = categoriaRepository.findById(dto.getIdCategoria())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría no encontrada"));
        master.setCategoria(cat);

        com.skycel.backend.domain.enums.TipoProducto tipo;
        try {
            tipo = com.skycel.backend.domain.enums.TipoProducto.valueOf(dto.getTipo().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de producto inválido. Use CELULAR, ACCESORIO o SERVICIO.");
        }
        master.setTipo(tipo);

        return productoMapper.toResponse(productoMasterRepository.save(master));
    }

    @Transactional
    public void eliminarMaster(Integer id) {
        productoMasterRepository.findById(id).ifPresent(m -> {
            m.setActivo(false);
            productoMasterRepository.save(m);
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PRODUCTO (SKU / Stock por tienda)
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerStockPorTienda(Integer codti) {
        return productoRepository.findByTienda_CodtiAndActivoTrue(codti).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerStockDisponiblePorTienda(Integer codti) {
        return productoRepository.findAvailableStockByTienda(codti).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Transactional
    public ProductoResponseDTO crearProducto(ProductoRequestDTO dto) {

        Tienda tienda = tiendaRepository.findById(dto.getCodti())   // ← usa el codti del request
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tienda no encontrada: " + dto.getCodti()));

        // 1. Resolver o crear ProductoMaster
        ProductoMaster master;
        if (dto.getIdProductoMaster() != null) {
            master = productoMasterRepository.findById(Math.toIntExact(dto.getIdProductoMaster()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto Master no encontrado"));
        } else {
            // Resolver tipo de producto primero — decide cómo se arma el nombre y qué
            // campos (marca/modelo vs descripción/descripción2) aplican.
            TipoProducto tipo = TipoProducto.ACCESORIO;
            if (dto.getTipoMaster() != null) {
                try {
                    tipo = TipoProducto.valueOf(dto.getTipoMaster().toUpperCase().trim());
                } catch (IllegalArgumentException e) {
                    // ignorar, se queda el default
                }
            }

            String nombreComputado = construirNombreBase(tipo, dto);
            if (nombreComputado == null || nombreComputado.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        esEquipoConImei(tipo)
                                ? "Debes indicar marca y modelo para registrar un equipo."
                                : "Debes indicar una descripción para registrar el producto.");
            }

            Optional<ProductoMaster> existingMaster = productoMasterRepository.findByNombreBaseIgnoreCaseAndActivoTrue(nombreComputado);
            if (existingMaster.isPresent()) {
                master = existingMaster.get();
            } else {
                // Resolver categoría (para ACCESORIO/SERVICIO, esta ES el "Tipo 2") — solo entre las de este mismo tipo
                final TipoProducto tipoFinal = tipo;
                Categoria cat = null;
                if (dto.getCategoriaMaster() != null) {
                    cat = categoriaRepository.findAll().stream()
                            .filter(c -> c.getNombre().equalsIgnoreCase(dto.getCategoriaMaster().trim()) && c.getTipo() == tipoFinal)
                            .findFirst().orElse(null);
                }
                if (cat == null) {
                    cat = categoriaRepository.findAll().stream().filter(c -> c.getTipo() == tipoFinal).findFirst()
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "No hay ninguna categoría de tipo " + tipoFinal + " registrada en el sistema."));
                }

                boolean esEquipo = esEquipoConImei(tipo);
                boolean esServicio = tipo == TipoProducto.SERVICIO;

                master = ProductoMaster.builder()
                        .nombreBase(nombreComputado)
                        .categoria(cat)
                        .tipo(tipo)
                        .marca(esEquipo ? trimOrNull(dto.getMarca()) : null)
                        .modelo(esEquipo ? trimOrNull(dto.getModelo()) : null)
                        .especificaciones(!esEquipo ? trimOrNull(dto.getDescripcion()) : null)
                        .notaAdicional(esServicio ? trimOrNull(dto.getDescripcion2()) : null)
                        .compatibilidad(!esEquipo ? trimOrNull(dto.getCompatibilidad()) : null)
                        .tiempoEstimadoMin(esServicio ? validarTiempo(dto.getTiempoEstimadoMin()) : null)
                        .diasGarantia(validarDiasGarantia(dto.getDiasGarantia()))
                        .activo(true)
                        .build();
                master = productoMasterRepository.save(master);
            }
        }

        // 2. Resolver Magnitud
        Short idMag = dto.getIdMagnitud();
        if (idMag == null) idMag = 1; // default a 1 (Pza)
        Short finalIdMag = idMag;
        Magnitud mag = magnitudRepository.findById(idMag)
                .orElseGet(() -> magnitudRepository.findAll().stream()
                        .filter(m -> m.getIdmagnitud().equals(finalIdMag)).findFirst()
                        .orElseGet(() -> magnitudRepository.findAll().stream().findFirst()
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay unidades de magnitud registradas."))));

        // Generar codpro si viene vacío
        String codpro = dto.getCodpro();
        if (codpro == null || codpro.trim().isEmpty()) {
            codpro = generarCodigoPara(master);
        } else {
            codpro = codpro.trim();
        }

        // Validar que no exista el codpro en esa tienda
        if (productoRepository.findByCodproAndTienda_Codti(codpro, dto.getCodti()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe el código '" + codpro + "' en esa tienda.");

        // Validaciones especiales por tipo de producto
        List<UnidadEquipoDTO> unidades = List.of();
        if (esEquipoConImei(master.getTipo())) {
            unidades = unidadesDe(dto.getImeis(), dto.getUnidades());
            BigDecimal stock = dto.getStock() != null ? dto.getStock() : BigDecimal.ZERO;
            int cantidad = stock.intValue();
            if (stock.compareTo(BigDecimal.valueOf(cantidad)) != 0 || cantidad < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El stock para equipos debe ser un número entero no negativo.");
            }
            if (cantidad > 0) {
                if (unidades.size() != cantidad) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Debe proporcionar exactamente " + cantidad + " IMEIs para el stock inicial.");
                }
                for (UnidadEquipoDTO u : unidades) {
                    if (u.getImei() == null || !u.getImei().matches("[A-Za-z0-9]{5,20}")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El identificador '" + u.getImei() + "' debe tener entre 5 y 20 caracteres alfanuméricos (IMEI o número de serie).");
                    }
                    if (productoImeiRepository.existsByImei(u.getImei())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "El identificador '" + u.getImei() + "' ya está registrado en el sistema.");
                    }
                }
            }
        }

        Producto producto = productoMapper.toProductoEntity(dto);
        producto.setCodpro(codpro);
        producto.setProductoMaster(master);
        producto.setTienda(tienda);
        producto.setMagnitud(mag);
        producto.setActivo(true);

        // Si es servicio, forzar stock a 0
        if (master.getTipo() == com.skycel.backend.domain.enums.TipoProducto.SERVICIO) {
            producto.setStock(BigDecimal.ZERO);
        }
        producto.setStockMinimo(validarStockMinimo(dto.getStockMinimo(), master.getTipo()));

        // Opcionales
        if (dto.getIdColor()     != null) producto.setColor(colorRepository.findById(dto.getIdColor()).orElse(null));
        if (dto.getIdProveedor() != null) producto.setProveedor(proveedorRepository.findById(dto.getIdProveedor()).orElse(null));
        if (dto.getIdSeccion()   != null) producto.setSeccion(seccionRepository.findById(dto.getIdSeccion()).orElse(null));

        Producto saved = productoRepository.save(producto);
        movimientoInventarioService.registrar(saved, BigDecimal.ZERO, "ALTA", "Stock inicial", null);

        // Registrar IMEIs si es celular y hay stock
        for (UnidadEquipoDTO u : unidades) {
            productoImeiRepository.save(nuevaUnidad(saved, u));
        }

        return toDto(saved);
    }

    // ── Actualizar precios / datos ────────────────────────────────────────────

    @Transactional
    public ProductoResponseDTO actualizar(String codpro, ProductoUpdateDTO dto) {
        Producto p = productoRepository.findByCodpro(codpro)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto no encontrado: " + codpro));

        if (dto.getPrecioCompra() != null) p.setPreciopro(dto.getPrecioCompra());
        if (dto.getPrecioVenta()  != null) p.setPreciopub(dto.getPrecioVenta());
        if (dto.getIdColor()      != null) p.setColor(colorRepository.findById(dto.getIdColor()).orElse(null));
        if (dto.getIdProveedor()  != null) p.setProveedor(proveedorRepository.findById(dto.getIdProveedor()).orElse(null));
        if (dto.getIdSeccion()    != null) p.setSeccion(seccionRepository.findById(dto.getIdSeccion()).orElse(null));
        if (dto.getIdMagnitud()   != null) p.setMagnitud(magnitudRepository.findById(dto.getIdMagnitud())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Magnitud no encontrada")));
        if (dto.getStockMinimo()  != null) p.setStockMinimo(validarStockMinimo(dto.getStockMinimo(), p.getProductoMaster().getTipo()));

        return toDto(productoRepository.save(p));
    }

    /** Atributos del artículo que no dependen de la tienda: compatibilidad, tiempo estimado y garantía. */
    @Transactional
    public ProductoMasterResponseDTO actualizarMaster(Integer idprodmaster, ProductoMasterUpdateDTO dto) {
        ProductoMaster master = productoMasterRepository.findById(idprodmaster)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto maestro no encontrado: " + idprodmaster));

        if (dto.getCompatibilidad() != null) master.setCompatibilidad(trimOrNull(dto.getCompatibilidad()));
        if (dto.getTiempoEstimadoMin() != null) {
            if (master.getTipo() != com.skycel.backend.domain.enums.TipoProducto.SERVICIO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tiempo estimado solo aplica a servicios.");
            }
            master.setTiempoEstimadoMin(validarTiempo(dto.getTiempoEstimadoMin()));
        }
        if (dto.getDiasGarantia() != null) master.setDiasGarantia(validarDiasGarantia(dto.getDiasGarantia()));

        return productoMapper.toResponse(productoMasterRepository.save(master));
    }

    /** Productos de la tienda que llegaron a su umbral de reposición. */
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerBajoStock(Integer codti) {
        return productoRepository.findBajoStockByTienda(codti).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Accesorios con stock de la tienda que sirven para un modelo (incluye los "Universal"). */
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerAccesoriosCompatibles(Integer codti, String modelo) {
        if (modelo == null || modelo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el modelo (parámetro 'con').");
        }
        return productoRepository.findAccesoriosCompatibles(codti, modelo.trim()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerProductoPorImei(String imei) {
        ProductoImei pi = productoImeiRepository.findByImei(imei)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El IMEI '" + imei + "' no está registrado."));
        return toDto(pi.getProducto());
    }

    // ── Ajustar stock ─────────────────────────────────────────────────────────

    @Transactional
    public ProductoResponseDTO ajustarStock(String codpro, StockAjusteDTO dto) {
        Producto p = productoRepository.findByCodpro(codpro)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto no encontrado: " + codpro));

        com.skycel.backend.domain.enums.TipoProducto tipoProd = p.getProductoMaster().getTipo();

        if (tipoProd == com.skycel.backend.domain.enums.TipoProducto.SERVICIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede ajustar stock en artículos de tipo SERVICIO.");
        }

        BigDecimal stockActual = p.getStock() != null ? p.getStock() : BigDecimal.ZERO;
        BigDecimal cantidad    = dto.getCantidad() != null ? dto.getCantidad() : BigDecimal.ZERO;

        if (esEquipoConImei(tipoProd)) {
            int cantInt = cantidad.intValue();
            if (cantidad.compareTo(BigDecimal.valueOf(cantInt)) != 0 || cantInt < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad para celulares debe ser un número entero no negativo.");
            }

            List<UnidadEquipoDTO> unidades = unidadesDe(dto.getImeis(), dto.getUnidades());
            List<String> imeis = unidades.stream().map(UnidadEquipoDTO::getImei).collect(Collectors.toList());

            if (cantInt > 0) {
                if (unidades.size() != cantInt) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Debe proporcionar exactamente " + cantInt + " IMEIs para esta operación.");
                }

                // Validar los IMEIs/números de serie proporcionados
                for (String imei : imeis) {
                    if (imei == null || !imei.matches("[A-Za-z0-9]{5,20}")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El identificador '" + imei + "' debe tener entre 5 y 20 caracteres alfanuméricos (IMEI o número de serie).");
                    }
                }
            }

            switch (dto.getTipo().toUpperCase()) {
                case "ENTRADA" -> {
                    // Validar que no existan
                    for (String imei : imeis) {
                        if (productoImeiRepository.existsByImei(imei)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "El IMEI '" + imei + "' ya está registrado en el sistema.");
                        }
                    }
                    // Insertar
                    for (UnidadEquipoDTO u : unidades) {
                        productoImeiRepository.save(nuevaUnidad(p, u));
                    }
                    p.setStock(stockActual.add(cantidad));
                }
                case "SALIDA" -> {
                    // Validar que existan y estén DISPONIBLES para este producto
                    for (String imei : imeis) {
                        ProductoImei pi = productoImeiRepository.findByImei(imei)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        "El IMEI '" + imei + "' no existe."));
                        if (!pi.getProducto().getIdproducto().equals(p.getIdproducto())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "El IMEI '" + imei + "' no pertenece a este producto.");
                        }
                        if (!"DISPONIBLE".equals(pi.getEstado())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "El IMEI '" + imei + "' no está disponible (Estado: " + pi.getEstado() + ").");
                        }
                    }
                    // Dar de baja / Marcar como VENDIDO o DEBAJA
                    String nuevoEstado = "DEBAJA";
                    if (dto.getComentario() != null && dto.getComentario().toUpperCase().contains("VENTA")) {
                        nuevoEstado = "VENDIDO";
                    }
                    for (String imei : imeis) {
                        ProductoImei pi = productoImeiRepository.findByImei(imei).get();
                        pi.setEstado(nuevoEstado);
                        productoImeiRepository.save(pi);
                    }
                    BigDecimal nuevo = stockActual.subtract(cantidad);
                    if (nuevo.compareTo(BigDecimal.ZERO) < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Stock insuficiente. Actual: " + stockActual + ", Salida solicitada: " + cantidad);
                    }
                    p.setStock(nuevo);
                }
                case "AJUSTE" -> {
                    // Para AJUSTE:
                    // 1. Validar que los IMEIs no pertenezcan a otro producto
                    for (String imei : imeis) {
                        productoImeiRepository.findByImei(imei).ifPresent(pi -> {
                            if (!pi.getProducto().getIdproducto().equals(p.getIdproducto())) {
                                throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "El IMEI '" + imei + "' pertenece a otro producto.");
                            }
                        });
                    }
                    // 2. Dar de baja todos los IMEIs actualmente DISPONIBLES de este producto
                    List<ProductoImei> actuales = productoImeiRepository.findByProducto_IdproductoAndEstado(p.getIdproducto(), "DISPONIBLE");
                    for (ProductoImei pi : actuales) {
                        pi.setEstado("DEBAJA");
                        productoImeiRepository.save(pi);
                    }
                    // 3. Registrar los nuevos, reactivando los que ya existían o insertando nuevos
                    for (UnidadEquipoDTO u : unidades) {
                        Optional<ProductoImei> piOpt = productoImeiRepository.findByImei(u.getImei());
                        if (piOpt.isPresent()) {
                            ProductoImei pi = piOpt.get();
                            pi.setEstado("DISPONIBLE");
                            // Si se enviaron datos propios de la unidad, se actualizan; si no, se conservan los que tenía
                            if (u.getCondicion() != null) pi.setCondicion(normalizarCondicion(u.getCondicion()));
                            if (u.getCostoUnitario() != null) pi.setCostoUnitario(u.getCostoUnitario());
                            if (u.getPrecioVenta() != null) pi.setPrecioVentaOverride(u.getPrecioVenta());
                            productoImeiRepository.save(pi);
                        } else {
                            productoImeiRepository.save(nuevaUnidad(p, u));
                        }
                    }
                    p.setStock(cantidad);
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tipo de ajuste inválido. Use ENTRADA, SALIDA o AJUSTE.");
            }
        } else {
            // Producto estándar (ACCESORIO)
            switch (dto.getTipo().toUpperCase()) {
                case "ENTRADA" -> p.setStock(stockActual.add(cantidad));
                case "SALIDA"  -> {
                    BigDecimal nuevo = stockActual.subtract(cantidad);
                    if (nuevo.compareTo(BigDecimal.ZERO) < 0)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Stock insuficiente. Actual: " + stockActual + ", Salida solicitada: " + cantidad);
                    p.setStock(nuevo);
                }
                case "AJUSTE"  -> p.setStock(cantidad);
                default        -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tipo inválido. Use: ENTRADA, SALIDA o AJUSTE");
            }
        }

        movimientoInventarioService.registrar(p, stockActual, dto.getTipo().toUpperCase(), dto.getComentario(), null);
        return toDto(productoRepository.save(p));
    }

    // ── Desactivar ────────────────────────────────────────────────────────────

    @Transactional
    public void eliminarProducto(String codpro) {
        productoRepository.findByCodpro(codpro).ifPresent(p -> {
            p.setActivo(false);
            productoRepository.save(p);
        });
    }

    // ── Precio por IMEI ─────────────────────────────────────────────────────

    @Transactional
    public ImeiInfoDTO actualizarPrecioImei(String imei, ImeiPrecioUpdateDTO dto) {
        ProductoImei pi = productoImeiRepository.findByImei(imei)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El IMEI '" + imei + "' no está registrado."));

        if (!"DISPONIBLE".equals(pi.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede ajustar el precio de un IMEI disponible (estado actual: " + pi.getEstado() + ").");
        }

        BigDecimal nuevoPrecio = dto.getPrecioVenta();
        if (nuevoPrecio != null && nuevoPrecio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio debe ser mayor a 0.");
        }

        pi.setPrecioVentaOverride(nuevoPrecio); // null = quitar el override, vuelve al precio del modelo
        pi = productoImeiRepository.save(pi);

        return toImeiInfo(pi, pi.getProducto());
    }

    /** Corrige la condición o el costo de una unidad disponible. */
    @Transactional
    public ImeiInfoDTO actualizarImei(String imei, ImeiUpdateDTO dto) {
        ProductoImei pi = productoImeiRepository.findByImei(imei)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El IMEI '" + imei + "' no está registrado."));

        if (!"DISPONIBLE".equals(pi.getEstado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede modificar una unidad disponible (estado actual: " + pi.getEstado() + ").");
        }
        if (dto.getCondicion() != null) pi.setCondicion(normalizarCondicion(dto.getCondicion()));
        if (dto.getCostoUnitario() != null) {
            if (dto.getCostoUnitario().signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo no puede ser negativo.");
            }
            pi.setCostoUnitario(dto.getCostoUnitario());
        }
        return toImeiInfo(productoImeiRepository.save(pi), pi.getProducto());
    }

    // ── Mapeo enriquecido ─────────────────────────────────────────────────────

    private ProductoResponseDTO toDto(Producto p) {
        ProductoResponseDTO dto = productoMapper.toProductoResponse(p);
        // Campos adicionales que el mapper no cubre
        if (p.getTienda() != null) {
            dto.setCodti(p.getTienda().getCodti());
            dto.setNombreTienda(p.getTienda().getNombre());
        }
        if (p.getProductoMaster() != null && p.getProductoMaster().getCategoria() != null) {
            dto.setNombreCategoria(p.getProductoMaster().getCategoria().getNombre());
        }
        boolean esServicio = false;
        if (p.getProductoMaster() != null) {
            dto.setMarca(p.getProductoMaster().getMarca());
            dto.setModelo(p.getProductoMaster().getModelo());
            dto.setDescripcion(p.getProductoMaster().getEspecificaciones());
            dto.setDescripcion2(p.getProductoMaster().getNotaAdicional());
            dto.setCompatibilidad(p.getProductoMaster().getCompatibilidad());
            dto.setTiempoEstimadoMin(p.getProductoMaster().getTiempoEstimadoMin());
            dto.setDiasGarantia(p.getProductoMaster().getDiasGarantia());
            esServicio = p.getProductoMaster().getTipo() == com.skycel.backend.domain.enums.TipoProducto.SERVICIO;
        }
        // Alerta de reposición: stock <= umbral, solo si hay umbral definido y no es un servicio
        BigDecimal minimo = p.getStockMinimo() != null ? p.getStockMinimo() : BigDecimal.ZERO;
        BigDecimal stock = p.getStock() != null ? p.getStock() : BigDecimal.ZERO;
        dto.setStockMinimo(minimo);
        dto.setBajoStock(!esServicio && minimo.signum() > 0 && stock.compareTo(minimo) <= 0);

        if (p.getProductoMaster() != null && esEquipoConImei(p.getProductoMaster().getTipo())) {
            List<ImeiInfoDTO> availableImeis = productoImeiRepository.findByProducto_IdproductoAndEstado(p.getIdproducto(), "DISPONIBLE")
                    .stream()
                    .map(pi -> toImeiInfo(pi, p))
                    .collect(Collectors.toList());
            dto.setImeisDisponibles(availableImeis);
        }
        dto.setActivo(p.getActivo());
        return dto;
    }

    /** Datos de una unidad: precio y costo efectivos (propios si los tiene, si no los del modelo) y su condición. */
    private ImeiInfoDTO toImeiInfo(ProductoImei pi, Producto p) {
        boolean precioPropio = pi.getPrecioVentaOverride() != null;
        boolean costoPropio = pi.getCostoUnitario() != null;
        return new ImeiInfoDTO(
                pi.getImei(),
                precioPropio ? pi.getPrecioVentaOverride() : p.getPreciopub(),
                precioPropio,
                pi.getCondicion() != null ? pi.getCondicion() : "NUEVO",
                costoPropio ? pi.getCostoUnitario() : p.getPreciopro(),
                costoPropio);
    }

    // ── Unidades de equipo (IMEI / serie) ─────────────────────────────────────

    /**
     * Unifica las dos formas de enviar unidades: la lista simple `imeis` (todas nuevas, con el costo
     * y el precio del modelo) o `unidades`, con condición, costo y precio propios. Devuelve siempre
     * una lista (vacía si no se envió ninguna). No se aceptan ambas a la vez.
     */
    private List<UnidadEquipoDTO> unidadesDe(List<String> imeis, List<UnidadEquipoDTO> unidades) {
        boolean hayImeis = imeis != null && !imeis.isEmpty();
        boolean hayUnidades = unidades != null && !unidades.isEmpty();
        if (hayImeis && hayUnidades) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Envíe 'imeis' o 'unidades', no ambas.");
        }
        if (hayUnidades) {
            for (UnidadEquipoDTO u : unidades) {
                normalizarCondicion(u.getCondicion());
                if (u.getCostoUnitario() != null && u.getCostoUnitario().signum() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El costo de la unidad no puede ser negativo.");
                }
                if (u.getPrecioVenta() != null && u.getPrecioVenta().signum() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El precio de la unidad debe ser mayor a 0.");
                }
            }
            return unidades;
        }
        List<UnidadEquipoDTO> simples = new ArrayList<>();
        if (hayImeis) {
            for (String imei : imeis) {
                UnidadEquipoDTO u = new UnidadEquipoDTO();
                u.setImei(imei);
                simples.add(u);
            }
        }
        return simples;
    }

    private ProductoImei nuevaUnidad(Producto p, UnidadEquipoDTO u) {
        return ProductoImei.builder()
                .producto(p)
                .imei(u.getImei())
                .estado("DISPONIBLE")
                .condicion(normalizarCondicion(u.getCondicion()))
                .costoUnitario(u.getCostoUnitario())
                .precioVentaOverride(u.getPrecioVenta())
                .build();
    }

    /** Mayúsculas y validación; sin valor equivale a NUEVO. */
    private String normalizarCondicion(String condicion) {
        if (condicion == null || condicion.isBlank()) return "NUEVO";
        String c = condicion.trim().toUpperCase();
        if (!CONDICIONES.contains(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Condición inválida: '" + condicion + "'. Use NUEVO, USADO o REACONDICIONADO.");
        }
        return c;
    }

    // ── Validaciones de atributos ─────────────────────────────────────────────

    /** Umbral de reposición: no negativo y sin sentido en servicios (siempre 0). */
    private BigDecimal validarStockMinimo(BigDecimal minimo, com.skycel.backend.domain.enums.TipoProducto tipo) {
        if (minimo == null) return BigDecimal.ZERO;
        if (minimo.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El stock mínimo no puede ser negativo.");
        }
        if (tipo == com.skycel.backend.domain.enums.TipoProducto.SERVICIO && minimo.signum() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El stock mínimo no aplica a servicios.");
        }
        return minimo;
    }

    private Integer validarTiempo(Integer minutos) {
        if (minutos != null && minutos < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tiempo estimado debe ser de al menos 1 minuto.");
        }
        return minutos;
    }

    private Integer validarDiasGarantia(Integer dias) {
        if (dias != null && dias < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los días de garantía no pueden ser negativos.");
        }
        return dias;
    }

    /**
     * Arma nombreBase a partir de los campos estructurados según el tipo, salvo que
     * el cliente mande un nombreMaster explícito (override manual).
     *   EQUIPO:   "{marca} {modelo}"           ej. "iPhone 17 Pro Max 8/256gb"
     *   SERVICIO: "{descripcion} — {descripcion2}" ej. "Cambio de Pantalla — Samsung A56 5G"
     *   ACCESORIO/otros: "{descripcion}"       ej. "AirPods Pro 2 Gen"
     */
    /** CELULAR y TABLET son "equipos": marca/modelo en vez de descripción, y unidades por IMEI/serie. */
    private boolean esEquipoConImei(TipoProducto tipo) {
        return tipo == TipoProducto.CELULAR || tipo == TipoProducto.TABLET;
    }

    private String construirNombreBase(TipoProducto tipo, ProductoRequestDTO dto) {
        if (dto.getNombreMaster() != null && !dto.getNombreMaster().isBlank()) {
            return dto.getNombreMaster().trim();
        }
        if (esEquipoConImei(tipo)) {
            String marca = dto.getMarca() != null ? dto.getMarca().trim() : "";
            String modelo = dto.getModelo() != null ? dto.getModelo().trim() : "";
            return (marca + " " + modelo).trim();
        }
        String descripcion = dto.getDescripcion() != null ? dto.getDescripcion().trim() : "";
        if (tipo == TipoProducto.SERVICIO
                && dto.getDescripcion2() != null && !dto.getDescripcion2().isBlank()) {
            return descripcion + " — " + dto.getDescripcion2().trim();
        }
        return descripcion;
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String generarCodigoPorCategoria(Categoria categoria) {
        if (categoria == null || categoria.getCodigo() == null || categoria.getCodigo().isBlank()) {
            String nombreCat = categoria != null ? categoria.getNombre() : "(sin categoría)";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La categoría '" + nombreCat + "' no tiene un código corto configurado (ej. \"AUD\"). " +
                            "Un administrador debe asignárselo antes de registrar accesorios en ella.");
        }
        categoriaFolioRepository.incrementar(categoria.getIdcat());
        Long folio = categoriaFolioRepository.obtenerUltimoFolioGenerado(categoria.getIdcat());
        return categoria.getCodigo().trim().toUpperCase() + "-" + String.format("%06d", folio);
    }

    /**
     * Código nuevo para un producto de este maestro. Equipos: CEL-000001 (contador propio).
     * Accesorios y servicios: {código de su categoría}-000001. También lo usa el traspaso al crear
     * el producto en la tienda destino.
     */
    public String generarCodigoPara(ProductoMaster master) {
        return master.getTipo() == com.skycel.backend.domain.enums.TipoProducto.CELULAR
                ? generarCodigoEquipo()
                : generarCodigoPorCategoria(master.getCategoria());
    }

    /** Código consecutivo de un equipo (CEL-000001), con un contador propio. */
    private String generarCodigoEquipo() {
        categoriaFolioRepository.incrementar(FOLIO_EQUIPOS);
        Long folio = categoriaFolioRepository.obtenerUltimoFolioGenerado(FOLIO_EQUIPOS);
        return "CEL-" + String.format("%06d", folio);
    }
}