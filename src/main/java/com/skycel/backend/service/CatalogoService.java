package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.request.CatalogoSimpleRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.dto.response.CatalogoSimpleResponseDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.mapper.CatalogoMapper;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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

    // ==========================================
    // === LECTURAS (Solo activos) + CACHE    ===
    // ==========================================

    @Cacheable(value = "categoriasCache")
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> obtenerCategoriasActivas() {
        return categoriaRepository.findByActivoTrue().stream()
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
    public List<CatalogoSimpleResponseDTO> obtenerMagnitudesActivas() {
        return magnitudRepository.findByActivoTrue().stream()
                .map(catalogoMapper::toMagnitudResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "proveedoresCache")
    @Transactional(readOnly = true)
    public List<CatalogoSimpleResponseDTO> obtenerProveedoresActivos() {
        return proveedorRepository.findByActivoTrue().stream()
                .map(catalogoMapper::toProveedorResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "seccionesCache")
    @Transactional(readOnly = true)
    public List<CatalogoSimpleResponseDTO> obtenerSeccionesActivas() {
        return seccionRepository.findByActivoTrue().stream()
                .map(catalogoMapper::toSeccionResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Tienda> obtenerTiendas() {
        return tiendaRepository.findAll();
    }

    // ==========================================
    // === ESCRITURAS (CRUD y Soft-Delete)    ===
    // ==========================================

    @CacheEvict(value = "categoriasCache", allEntries = true)
    @Transactional
    public CategoriaResponseDTO crearCategoria(CategoriaRequestDTO dto) {
        Categoria categoria = catalogoMapper.toEntity(dto);
        if (dto.getIdCategoriaSuperior() != null) {
            Categoria superior = categoriaRepository.findById(dto.getIdCategoriaSuperior())
                    .orElseThrow(() -> new RuntimeException("Categoría superior no encontrada"));
            categoria.setCategoriaSuperior(superior);
        }
        return catalogoMapper.toCategoriaResponse(categoriaRepository.save(categoria));
    }

    @CacheEvict(value = {"coloresCache", "magnitudesCache", "proveedoresCache", "seccionesCache"}, allEntries = true)
    @Transactional
    public CatalogoSimpleResponseDTO crearCatalogoSimple(String tipo, CatalogoSimpleRequestDTO dto) {
        switch (tipo.toLowerCase()) {
            case "color": return catalogoMapper.toColorResponse(colorRepository.save(catalogoMapper.toColorEntity(dto)));
            case "magnitud": return catalogoMapper.toMagnitudResponse(magnitudRepository.save(catalogoMapper.toMagnitudEntity(dto)));
            case "proveedor": return catalogoMapper.toProveedorResponse(proveedorRepository.save(catalogoMapper.toProveedorEntity(dto)));
            case "seccion": return catalogoMapper.toSeccionResponse(seccionRepository.save(catalogoMapper.toSeccionEntity(dto)));
            default: throw new IllegalArgumentException("Tipo de catálogo no soportado: " + tipo);
        }
    }

    @CacheEvict(value = {"coloresCache", "magnitudesCache", "proveedoresCache", "seccionesCache"}, allEntries = true)
    @Transactional
    public void eliminarCatalogoSimple(String tipo, Short id) {
        switch (tipo.toLowerCase()) {
            case "color": 
                colorRepository.findById(id).ifPresent(c -> { c.setActivo(false); colorRepository.save(c); });
                break;
            case "magnitud": 
                magnitudRepository.findById(id).ifPresent(m -> { m.setActivo(false); magnitudRepository.save(m); });
                break;
            case "proveedor": 
                proveedorRepository.findById(id).ifPresent(p -> { p.setActivo(false); proveedorRepository.save(p); });
                break;
            case "seccion": 
                seccionRepository.findById(id).ifPresent(s -> { s.setActivo(false); seccionRepository.save(s); });
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
