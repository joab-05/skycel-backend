package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final CategoriaRepository categoriaRepository;
    private final ColorRepository colorRepository;
    private final MagnitudRepository magnitudRepository;
    private final ProveedorRepository proveedorRepository;
    private final SeccionRepository seccionRepository;
    private final TiendaRepository tiendaRepository;

    @Cacheable(value = "categoriasCache")
    @Transactional(readOnly = true)
    public List<Categoria> obtenerCategoriasActivas() {
        return categoriaRepository.findByActivoTrue();
    }

    @Cacheable(value = "coloresCache")
    @Transactional(readOnly = true)
    public List<Color> obtenerColoresActivos() {
        return colorRepository.findByActivoTrue();
    }

    @Cacheable(value = "magnitudesCache")
    @Transactional(readOnly = true)
    public List<Magnitud> obtenerMagnitudesActivas() {
        return magnitudRepository.findByActivoTrue();
    }

    @Cacheable(value = "proveedoresCache")
    @Transactional(readOnly = true)
    public List<Proveedor> obtenerProveedoresActivos() {
        return proveedorRepository.findByActivoTrue();
    }

    @Cacheable(value = "seccionesCache")
    @Transactional(readOnly = true)
    public List<Seccion> obtenerSeccionesActivas() {
        return seccionRepository.findByActivoTrue();
    }

    @Transactional(readOnly = true)
    public List<Tienda> obtenerTiendas() {
        return tiendaRepository.findAll();
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
