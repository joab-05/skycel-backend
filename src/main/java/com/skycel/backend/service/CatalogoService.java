package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.request.CatalogoSimpleRequestDTO;
import com.skycel.backend.domain.dto.request.MagnitudRequestDTO;
import com.skycel.backend.domain.dto.request.ProveedorRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.dto.response.CatalogoSimpleResponseDTO;
import com.skycel.backend.domain.dto.response.MagnitudResponseDTO;
import com.skycel.backend.domain.dto.response.ProveedorResponseDTO;
import com.skycel.backend.domain.dto.response.SeccionResponseDTO;
import com.skycel.backend.domain.dto.request.MagnitudRequestDTO;
import com.skycel.backend.domain.dto.request.ProveedorRequestDTO;
import com.skycel.backend.domain.dto.request.SeccionRequestDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.mapper.CatalogoMapper;
import com.skycel.backend.repository.*;
import com.skycel.backend.shared.exception.RecursoDuplicadoException;
import com.skycel.backend.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final CategoriaRepository categoriaRepository;
    private final ColorRepository colorRepository;
    private final MagnitudRepository magnitudRepository;
    private final ProveedorRepository proveedorRepository;
    private final SeccionRepository seccionRepository;
    private final TiendaRepository tiendaRepository;
    private final CatalogoMapper catalogoMapper;
    private final CategoriaService categoriaService;
    private final ProductoRepository productoRepository;
    private final ProductoMasterRepository productoMasterRepository;

    // ==========================================
    // === LECTURAS (Solo activos) + CACHE    ===
    // ==========================================

    @Cacheable(value = "categoriasCache")
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> obtenerCategoriasActivas() {
        return categoriaRepository.findByCategoriaSuperiorIsNullAndActivoTrue().stream()
                .map(catalogoMapper::toCategoriaResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "coloresCache")
    @Transactional(readOnly = true)
    public List<CatalogoSimpleResponseDTO> obtenerColoresActivos() {
        return colorRepository.findByActivoTrue().stream()
                .map(catalogoMapper::toColorResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "magnitudesCache")
    @Transactional(readOnly = true)
    public List<MagnitudResponseDTO> obtenerMagnitudesActivas() {
        return magnitudRepository.findByActivoTrue().stream()
                .map(catalogoMapper::toMagnitudResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "proveedoresCache")
    @Transactional(readOnly = true)
    public List<ProveedorResponseDTO> obtenerProveedoresActivos() {
        return proveedorRepository.findByActivoTrue().stream()
                .map(catalogoMapper::toProveedorResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "seccionesCache")
    @Transactional(readOnly = true)
    public List<SeccionResponseDTO> obtenerSeccionesActivas() {
        return seccionRepository.findByActivoTrue().stream()
                .map(catalogoMapper::toSeccionResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Tienda> obtenerTiendas() {
        return tiendaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ProveedorResponseDTO> obtenerTodosProveedores() {
        return proveedorRepository.findAll().stream()
                .map(catalogoMapper::toProveedorResponse)
                .collect(Collectors.toList());
    }

    // ==========================================
    // === ESCRITURAS (CRUD y Soft-Delete)    ===
    // ==========================================

    @CacheEvict(value = "proveedoresCache", allEntries = true)
    @Transactional
    public ProveedorResponseDTO actualizarProveedor(Short id, ProveedorRequestDTO dto) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor", id));
        catalogoMapper.updateProveedorFromDto(dto, proveedor);
        if (proveedor.getNombreFiscal() == null || proveedor.getNombreFiscal().isBlank()) {
            proveedor.setNombreFiscal(proveedor.getNombreCorto());   // la razón social es obligatoria en la base: sin ella se usa el nombre corto
        }
        return catalogoMapper.toProveedorResponse(proveedorRepository.save(proveedor));
    }

    // Las reglas del árbol de categorías (3 niveles, tipo heredado, código único, renombrado de artículos) viven
    // en CategoriaService: esta ruta del catálogo solo le delega y limpia el caché.
    @CacheEvict(value = "categoriasCache", allEntries = true)
    @Transactional
    public CategoriaResponseDTO crearCategoria(CategoriaRequestDTO dto) {
        return categoriaService.crear(dto);
    }

    // ========== PUT - Actualizar Categoría ==========

    @CacheEvict(value = "categoriasCache", allEntries = true)
    @Transactional
    public CategoriaResponseDTO actualizarCategoria(Short id, CategoriaRequestDTO dto) {
        return categoriaService.actualizar(id, dto);
    }

    /**
     * Cambia el nombre de un color. Los artículos que lo llevan en su nombre completo (categorías + descripción + color) se
     * renombran con él, salvo los de nombre puesto a mano.
     */
    @CacheEvict(value = "coloresCache", allEntries = true)
    @Transactional
    public CatalogoSimpleResponseDTO actualizarColor(Short id, CatalogoSimpleRequestDTO dto) {
        Color c = colorRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Color", id));
        String nombre = dto.getNombre().trim().replaceAll("\\s+", " ");
        if (nombre.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "El nombre no puede quedar vacío.");
        for (Color otro : colorRepository.findAll()) {
            if (!otro.getIdcolor().equals(id) && Boolean.TRUE.equals(otro.getActivo()) && otro.getNombre().equalsIgnoreCase(nombre)) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Ya existe el color '" + otro.getNombre() + "'.");
            }
        }
        java.util.List<com.skycel.backend.domain.entity.ProductoMaster> usan = productoMasterRepository.findByColor_Idcolor(id);
        java.util.Map<Integer, Boolean> armado = new java.util.HashMap<>();
        for (com.skycel.backend.domain.entity.ProductoMaster m : usan) armado.put(m.getIdprodmaster(), m.nombreCompleto().equalsIgnoreCase(m.getNombreBase()));
        c.setNombre(nombre);
        colorRepository.save(c);
        for (com.skycel.backend.domain.entity.ProductoMaster m : usan) {
            if (Boolean.TRUE.equals(armado.get(m.getIdprodmaster()))) {
                m.setNombreBase(m.nombreCompleto());
                productoMasterRepository.save(m);
            }
        }
        return catalogoMapper.toColorResponse(c);
    }

    /** Elimina (de forma lógica) un color que ya no sirve; solo si ningún producto o artículo activo lo usa. */
    @CacheEvict(value = "coloresCache", allEntries = true)
    @Transactional
    public void eliminarColor(Short id) {
        Color c = colorRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Color", id));
        long usan = productoRepository.countByColor_IdcolorAndActivoTrue(id) + productoMasterRepository.countByColor_IdcolorAndActivoTrue(id);
        if (usan > 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "No se puede eliminar el color '" + c.getNombre() + "': lo usan " + usan + " producto(s) o artículo(s) activo(s).");
        }
        c.setActivo(false);
        colorRepository.save(c);
    }

    /** Elimina (de forma lógica) un proveedor que ya no sirve; solo si ningún producto activo lo usa. */
    @CacheEvict(value = "proveedoresCache", allEntries = true)
    @Transactional
    public void eliminarProveedor(Short id) {
        Proveedor p = proveedorRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proveedor", id));
        long usan = productoRepository.countByProveedor_IdproveedorAndActivoTrue(id);
        if (usan > 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "No se puede eliminar al proveedor '" + p.getNombreCorto() + "': lo usan " + usan + " producto(s) activo(s).");
        }
        p.setActivo(false);
        proveedorRepository.save(p);
    }

    @CacheEvict(value = "coloresCache", allEntries = true)
    @Transactional
    public CatalogoSimpleResponseDTO crearColor(CatalogoSimpleRequestDTO dto) {
        return catalogoMapper.toColorResponse(colorRepository.save(catalogoMapper.toColorEntity(dto)));
    }

    /** Siembra la magnitud base ("Pieza") si no hay ninguna — sin al menos una, dar de alta cualquier producto falla. */
    @Transactional
    public void asegurarMagnitudBase() {
        if (magnitudRepository.count() == 0) {
            magnitudRepository.save(Magnitud.builder()
                    .nombre("Pieza").abreviatura("Pza").criterio((short) 0).activo(true).build());
        }
    }

    @CacheEvict(value = "magnitudesCache", allEntries = true)
    @Transactional
    public MagnitudResponseDTO crearMagnitud(MagnitudRequestDTO dto) {
        // Validar unicidad
        if (magnitudRepository.existsByNombreIgnoreCase(dto.getNombre())) {
            throw new RecursoDuplicadoException("Magnitud", "nombre", dto.getNombre());
        }
        Magnitud magnitud = catalogoMapper.toMagnitudEntity(dto);
        Magnitud saveMag = magnitudRepository.save(magnitud);

        return catalogoMapper.toMagnitudResponse(saveMag);
    }

    @CacheEvict(value = "magnitudesCache", allEntries = true)
    @Transactional
    public MagnitudResponseDTO actualizarMagnitud(Short id, MagnitudRequestDTO dto) {
        // Buscar y lanzar 404 si no existe
        Magnitud magnitud = magnitudRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Magnitud", id));

        // Validar unicidad solo si cambió el nombre
        if (!magnitud.getNombre().equalsIgnoreCase(dto.getNombre()) && magnitudRepository.existsByNombreIgnoreCaseAndIdmagnitudNot(dto.getNombre(), id)) {
            throw new RecursoDuplicadoException("Magnitud", "nombre", dto.getNombre());
        }
        // Actualizar campos
        catalogoMapper.updateMagnitudFromDto(dto, magnitud);
        Magnitud actualizada = magnitudRepository.save(magnitud);
        return catalogoMapper.toMagnitudResponse(actualizada);
    }

    @CacheEvict(value = "proveedoresCache", allEntries = true)
    @Transactional
    public ProveedorResponseDTO crearProveedor(ProveedorRequestDTO dto) {
        Proveedor proveedor = catalogoMapper.toProveedorEntity(dto);
        // La razón social es obligatoria en la base pero opcional en la API: sin ella se usa el nombre corto.
        if (proveedor.getNombreFiscal() == null || proveedor.getNombreFiscal().isBlank()) {
            proveedor.setNombreFiscal(proveedor.getNombreCorto());
        }
        return catalogoMapper.toProveedorResponse(proveedorRepository.save(proveedor));
    }

    @CacheEvict(value = "seccionesCache", allEntries = true)
    @Transactional
    public SeccionResponseDTO crearSeccion(SeccionRequestDTO dto) {
        Seccion seccion = catalogoMapper.toSeccionEntity(dto);
        Tienda tienda = tiendaRepository.findById(dto.getCodti())
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada con código: " + dto.getCodti()));
        seccion.setTienda(tienda);
        return catalogoMapper.toSeccionResponse(seccionRepository.save(seccion));
    }

    @CacheEvict(value = {"coloresCache", "magnitudesCache", "proveedoresCache", "seccionesCache", "categoriasCache"}, allEntries = true)
    @Transactional
    public void eliminarCatalogoSimple(String tipo, Short id) {
        switch (tipo.toLowerCase()) {
            case "color": 
                Color c = colorRepository.findById(id).orElseThrow(()-> new RecursoNoEncontradoException("Color", id));
                    c.setActivo(false);
                    colorRepository.save(c);
                break;
            case "magnitud":
                log.info("Intentando eliminar magnitud ID: {}", id);
                Magnitud magnitud = magnitudRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Magnitud", id));
                magnitud.setActivo(false);
                magnitudRepository.save(magnitud);
                log.info("Magnitud {} desactivada (soft delete)", id);
                break;
            case "proveedor": 
                proveedorRepository.findById(id).ifPresent(p -> { p.setActivo(false); proveedorRepository.save(p); });
                break;
            case "seccion": 
                seccionRepository.findById(id).ifPresent(s -> { s.setActivo(false); seccionRepository.save(s); });
                break;
            case "categoria":
                Categoria categoria = categoriaRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Categoria", id));
                categoria.setActivo(false);
                categoriaRepository.save(categoria);
                log.info("Categoria {} desactivada (soft delete)", id);
                break;
            default: throw new IllegalArgumentException("Tipo de catálogo no soportado: " + tipo);
        }
    }

    // --- Eviction methods placeholder for when entities are updated ---
    
    @CacheEvict(value = "categoriasCache", allEntries = true)
    public void limpiarCacheCategorias() {}

    @CacheEvict(value = "coloresCache", allEntries = true)
    public void limpiarCacheColores() {}

    @CacheEvict(value = "magnitudesCache", allEntries = true)
    public void limpiarCacheMagnitudes() {}

    @CacheEvict(value = "proveedoresCache", allEntries = true)
    public void limpiarCacheProveedores() {}

    @CacheEvict(value = "seccionesCache", allEntries = true)
    public void limpiarCacheSecciones() {}
}
