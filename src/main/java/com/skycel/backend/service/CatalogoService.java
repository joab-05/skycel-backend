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
        return catalogoMapper.toProveedorResponse(proveedorRepository.save(proveedor));
    }

    @CacheEvict(value = "categoriasCache", allEntries = true)
    @Transactional
    public CategoriaResponseDTO crearCategoria(CategoriaRequestDTO dto) {
        log.info("Creando categoría: {} bajo padre: {}",
                dto.getNombreCat(), dto.getIdCategoriaSuperior());
        Categoria categoria = catalogoMapper.toEntity(dto);
        // 1. Validar unicidad jerárquica
        validarUnicidadJerarquica(dto.getNombreCat(), dto.getIdCategoriaSuperior(), null);

        // 2. Asignar padre si existe
        if (dto.getIdCategoriaSuperior() != null) {
            Categoria superior = categoriaRepository.findById(dto.getIdCategoriaSuperior())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Categoría superior", dto.getIdCategoriaSuperior()));
            categoria.setCategoriaSuperior(superior);
        }

        Categoria guardada = categoriaRepository.save(categoria);
        log.info("Categoría creada con ID: {}", guardada.getIdcat());
        return catalogoMapper.toCategoriaResponse(categoriaRepository.save(categoria));
    }

    // ========== PUT - Actualizar Categoría ==========

    @CacheEvict(value = "categoriasCache", allEntries = true)
    @Transactional
    public CategoriaResponseDTO actualizarCategoria(Short id, CategoriaRequestDTO dto) {
        log.info("Actualizando categoría ID: {} - Nuevo nombre: {} - Nuevo padre: {}",
                id, dto.getNombreCat(), dto.getIdCategoriaSuperior());

        // 1. Buscar categoría existente
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría", id));

        // 2. Si cambia el padre, validar que no se cree un ciclo
        if (dto.getIdCategoriaSuperior() != null &&
                (categoria.getCategoriaSuperior() == null ||
                        !categoria.getCategoriaSuperior().getIdcat().equals(dto.getIdCategoriaSuperior()))) {

            validarNoCiclo(id, dto.getIdCategoriaSuperior());
        }

        // 3. Validar unicidad jerárquica (si cambió nombre o padre)
        boolean cambioNombre = !categoria.getNombre().equalsIgnoreCase(dto.getNombreCat());
        boolean cambioPadre = (categoria.getCategoriaSuperior() == null && dto.getIdCategoriaSuperior() != null) ||
                (categoria.getCategoriaSuperior() != null &&
                        !categoria.getCategoriaSuperior().getIdcat().equals(dto.getIdCategoriaSuperior()));

        if (cambioNombre || cambioPadre) {
            validarUnicidadJerarquica(dto.getNombreCat(), dto.getIdCategoriaSuperior(), id);
        }

        // 4. Actualizar relación de padre si cambió
        if (cambioPadre) {
            if (dto.getIdCategoriaSuperior() == null) {
                categoria.setCategoriaSuperior(null);
            } else {
                Categoria nuevoPadre = categoriaRepository.findById(dto.getIdCategoriaSuperior())
                        .orElseThrow(() -> new RecursoNoEncontradoException(
                                "Categoría superior", dto.getIdCategoriaSuperior()));
                categoria.setCategoriaSuperior(nuevoPadre);
            }
        }

        // 5. Actualizar demás campos
        catalogoMapper.updateEntityFromDto(dto, categoria);

        Categoria actualizada = categoriaRepository.save(categoria);
        log.info("Categoría {} actualizada exitosamente", id);

        return catalogoMapper.toCategoriaResponse(actualizada);
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

    private void validarUnicidadJerarquica(String nombre, Short idPadre, Short idExcluir) {
        boolean existe;

        if (idPadre == null) {
            // Es categoría raíz
            if (idExcluir == null) {
                existe = categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIsNull(nombre);
            } else {
                existe = categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIsNullAndIdcatNot(
                        nombre, idExcluir);
            }
        } else {
            // Es subcategoría
            if (idExcluir == null) {
                existe = categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIdcat(nombre, idPadre);
            } else {
                existe = categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIdcatAndIdcatNot(
                        nombre, idPadre, idExcluir);
            }
        }

        if (existe) {
            String nivel = idPadre == null ? "categorías principales" : "esta subcategoría";
            throw new RecursoDuplicadoException(
                    "Categoría",
                    "nombre",
                    String.format("'%s' en %s", nombre, nivel)
            );
        }
    }

    /**
     * Valida que no se cree un ciclo: una categoría no puede ser movida a una de sus subcategorías
     */
    private void validarNoCiclo(Short categoriaId, Short nuevoPadreId) {
        if (categoriaId.equals(nuevoPadreId)) {
            throw new IllegalArgumentException("Una categoría no puede ser subcategoría de sí misma");
        }

        // Verificar recursivamente que el nuevo padre no sea descendiente de la categoría
        List<Short> descendientes = obtenerTodosDescendientes(categoriaId);
        if (descendientes.contains(nuevoPadreId)) {
            throw new IllegalArgumentException(
                    "No se puede mover la categoría a una de sus subcategorías (crearía un ciclo)");
        }
    }

    private List<Short> obtenerTodosDescendientes(Short categoriaId) {
        List<Short> todos = new ArrayList<>();
        List<Short> hijosDirectos = categoriaRepository.findIdsByCategoriaSuperiorId(categoriaId);

        for (Short hijo : hijosDirectos) {
            todos.add(hijo);
            todos.addAll(obtenerTodosDescendientes(hijo)); // Recursión
        }

        return todos;
    }

}
