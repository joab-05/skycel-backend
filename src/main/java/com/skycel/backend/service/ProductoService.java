package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.*;
import com.skycel.backend.domain.dto.response.*;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.mapper.ProductoMapper;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
            com.skycel.backend.domain.enums.TipoProducto tipo = com.skycel.backend.domain.enums.TipoProducto.ACCESORIO;
            if (dto.getTipoMaster() != null) {
                try {
                    tipo = com.skycel.backend.domain.enums.TipoProducto.valueOf(dto.getTipoMaster().toUpperCase().trim());
                } catch (IllegalArgumentException e) {
                    // ignorar, se queda el default
                }
            }

            String nombreComputado = construirNombreBase(tipo, dto);
            if (nombreComputado == null || nombreComputado.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        tipo == com.skycel.backend.domain.enums.TipoProducto.CELULAR
                                ? "Debes indicar marca y modelo para registrar un equipo."
                                : "Debes indicar una descripción para registrar el producto.");
            }

            Optional<ProductoMaster> existingMaster = productoMasterRepository.findByNombreBaseIgnoreCaseAndActivoTrue(nombreComputado);
            if (existingMaster.isPresent()) {
                master = existingMaster.get();
            } else {
                // Resolver categoría (para ACCESORIO/SERVICIO, esta ES el "Tipo 2")
                Categoria cat = null;
                if (dto.getCategoriaMaster() != null) {
                    cat = categoriaRepository.findAll().stream()
                            .filter(c -> c.getNombre().equalsIgnoreCase(dto.getCategoriaMaster().trim()))
                            .findFirst().orElse(null);
                }
                if (cat == null) {
                    cat = categoriaRepository.findAll().stream().findFirst()
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay ninguna categoría registrada en el sistema."));
                }

                boolean esEquipo = tipo == com.skycel.backend.domain.enums.TipoProducto.CELULAR;
                boolean esServicio = tipo == com.skycel.backend.domain.enums.TipoProducto.SERVICIO;

                master = ProductoMaster.builder()
                        .nombreBase(nombreComputado)
                        .categoria(cat)
                        .tipo(tipo)
                        .marca(esEquipo ? trimOrNull(dto.getMarca()) : null)
                        .modelo(esEquipo ? trimOrNull(dto.getModelo()) : null)
                        .especificaciones(!esEquipo ? trimOrNull(dto.getDescripcion()) : null)
                        .notaAdicional(esServicio ? trimOrNull(dto.getDescripcion2()) : null)
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
            boolean usaCodigoPorCategoria = master.getTipo() == com.skycel.backend.domain.enums.TipoProducto.ACCESORIO
                    || master.getTipo() == com.skycel.backend.domain.enums.TipoProducto.SERVICIO;
            codpro = usaCodigoPorCategoria
                    ? generarCodigoPorCategoria(master.getCategoria())
                    : generarCodigoUnico(master.getTipo());
        } else {
            codpro = codpro.trim();
        }

        // Validar que no exista el codpro en esa tienda
        if (productoRepository.findByCodproAndTienda_Codti(codpro, dto.getCodti()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe el código '" + codpro + "' en esa tienda.");

        // Validaciones especiales por tipo de producto
        if (master.getTipo() == com.skycel.backend.domain.enums.TipoProducto.CELULAR) {
            BigDecimal stock = dto.getStock() != null ? dto.getStock() : BigDecimal.ZERO;
            int cantidad = stock.intValue();
            if (stock.compareTo(BigDecimal.valueOf(cantidad)) != 0 || cantidad < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El stock para celulares debe ser un número entero no negativo.");
            }
            if (cantidad > 0) {
                if (dto.getImeis() == null || dto.getImeis().size() != cantidad) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Debe proporcionar exactamente " + cantidad + " IMEIs para el stock inicial.");
                }
                for (String imei : dto.getImeis()) {
                    if (imei == null || !imei.matches("[A-Za-z0-9]{5,20}")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El identificador '" + imei + "' debe tener entre 5 y 20 caracteres alfanuméricos (IMEI o número de serie).");
                    }
                    if (productoImeiRepository.existsByImei(imei)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "El identificador '" + imei + "' ya está registrado en el sistema.");
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

        // Opcionales
        if (dto.getIdColor()     != null) producto.setColor(colorRepository.findById(dto.getIdColor()).orElse(null));
        if (dto.getIdProveedor() != null) producto.setProveedor(proveedorRepository.findById(dto.getIdProveedor()).orElse(null));
        if (dto.getIdSeccion()   != null) producto.setSeccion(seccionRepository.findById(dto.getIdSeccion()).orElse(null));

        Producto saved = productoRepository.save(producto);

        // Registrar IMEIs si es celular y hay stock
        if (master.getTipo() == com.skycel.backend.domain.enums.TipoProducto.CELULAR && dto.getImeis() != null) {
            for (String imei : dto.getImeis()) {
                ProductoImei prodImei = ProductoImei.builder()
                        .producto(saved)
                        .imei(imei)
                        .estado("DISPONIBLE")
                        .build();
                productoImeiRepository.save(prodImei);
            }
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

        return toDto(productoRepository.save(p));
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

        if (tipoProd == com.skycel.backend.domain.enums.TipoProducto.CELULAR) {
            int cantInt = cantidad.intValue();
            if (cantidad.compareTo(BigDecimal.valueOf(cantInt)) != 0 || cantInt < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad para celulares debe ser un número entero no negativo.");
            }

            if (cantInt > 0) {
                if (dto.getImeis() == null || dto.getImeis().size() != cantInt) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Debe proporcionar exactamente " + cantInt + " IMEIs para esta operación.");
                }

                // Validar los IMEIs/números de serie proporcionados
                for (String imei : dto.getImeis()) {
                    if (imei == null || !imei.matches("[A-Za-z0-9]{5,20}")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El identificador '" + imei + "' debe tener entre 5 y 20 caracteres alfanuméricos (IMEI o número de serie).");
                    }
                }
            }

            switch (dto.getTipo().toUpperCase()) {
                case "ENTRADA" -> {
                    // Validar que no existan
                    for (String imei : dto.getImeis()) {
                        if (productoImeiRepository.existsByImei(imei)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "El IMEI '" + imei + "' ya está registrado en el sistema.");
                        }
                    }
                    // Insertar
                    for (String imei : dto.getImeis()) {
                        ProductoImei pi = ProductoImei.builder()
                                .producto(p)
                                .imei(imei)
                                .estado("DISPONIBLE")
                                .build();
                        productoImeiRepository.save(pi);
                    }
                    p.setStock(stockActual.add(cantidad));
                }
                case "SALIDA" -> {
                    // Validar que existan y estén DISPONIBLES para este producto
                    for (String imei : dto.getImeis()) {
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
                    for (String imei : dto.getImeis()) {
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
                    for (String imei : dto.getImeis()) {
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
                    for (String imei : dto.getImeis()) {
                        Optional<ProductoImei> piOpt = productoImeiRepository.findByImei(imei);
                        if (piOpt.isPresent()) {
                            ProductoImei pi = piOpt.get();
                            pi.setEstado("DISPONIBLE");
                            productoImeiRepository.save(pi);
                        } else {
                            ProductoImei pi = ProductoImei.builder()
                                    .producto(p)
                                    .imei(imei)
                                    .estado("DISPONIBLE")
                                    .build();
                            productoImeiRepository.save(pi);
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

        BigDecimal precioModelo = pi.getProducto().getPreciopub();
        return new ImeiInfoDTO(
                pi.getImei(),
                pi.getPrecioVentaOverride() != null ? pi.getPrecioVentaOverride() : precioModelo,
                pi.getPrecioVentaOverride() != null);
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
        if (p.getProductoMaster() != null) {
            dto.setMarca(p.getProductoMaster().getMarca());
            dto.setModelo(p.getProductoMaster().getModelo());
            dto.setDescripcion(p.getProductoMaster().getEspecificaciones());
            dto.setDescripcion2(p.getProductoMaster().getNotaAdicional());
        }
        if (p.getProductoMaster() != null && p.getProductoMaster().getTipo() == com.skycel.backend.domain.enums.TipoProducto.CELULAR) {
            BigDecimal precioModelo = p.getPreciopub();
            List<ImeiInfoDTO> availableImeis = productoImeiRepository.findByProducto_IdproductoAndEstado(p.getIdproducto(), "DISPONIBLE")
                    .stream()
                    .map(pi -> new ImeiInfoDTO(
                            pi.getImei(),
                            pi.getPrecioVentaOverride() != null ? pi.getPrecioVentaOverride() : precioModelo,
                            pi.getPrecioVentaOverride() != null))
                    .collect(Collectors.toList());
            dto.setImeisDisponibles(availableImeis);
        }
        dto.setActivo(p.getActivo());
        return dto;
    }

    /**
     * Arma nombreBase a partir de los campos estructurados según el tipo, salvo que
     * el cliente mande un nombreMaster explícito (override manual).
     *   EQUIPO:   "{marca} {modelo}"           ej. "iPhone 17 Pro Max 8/256gb"
     *   SERVICIO: "{descripcion} — {descripcion2}" ej. "Cambio de Pantalla — Samsung A56 5G"
     *   ACCESORIO/otros: "{descripcion}"       ej. "AirPods Pro 2 Gen"
     */
    private String construirNombreBase(com.skycel.backend.domain.enums.TipoProducto tipo, ProductoRequestDTO dto) {
        if (dto.getNombreMaster() != null && !dto.getNombreMaster().isBlank()) {
            return dto.getNombreMaster().trim();
        }
        if (tipo == com.skycel.backend.domain.enums.TipoProducto.CELULAR) {
            String marca = dto.getMarca() != null ? dto.getMarca().trim() : "";
            String modelo = dto.getModelo() != null ? dto.getModelo().trim() : "";
            return (marca + " " + modelo).trim();
        }
        String descripcion = dto.getDescripcion() != null ? dto.getDescripcion().trim() : "";
        if (tipo == com.skycel.backend.domain.enums.TipoProducto.SERVICIO
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

    private String generarCodigoUnico(com.skycel.backend.domain.enums.TipoProducto tipo) {
        String prefix = switch (tipo) {
            case CELULAR -> "CEL-";
            case SERVICIO -> "SRV-";
            default -> "ACC-";
        };

        String codpro;
        do {
            String randomPart = java.util.UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase();
            codpro = prefix + randomPart;
        } while (productoRepository.findByCodpro(codpro).isPresent());

        return codpro;
    }
}