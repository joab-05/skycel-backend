package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ProductoMasterRequestDTO;
import com.skycel.backend.domain.dto.request.ProductoRequestDTO;
import com.skycel.backend.domain.dto.response.ProductoMasterResponseDTO;
import com.skycel.backend.domain.dto.response.ProductoResponseDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.mapper.ProductoMapper;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoMasterRepository productoMasterRepository;
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final TiendaRepository tiendaRepository;
    private final ColorRepository colorRepository;
    private final MagnitudRepository magnitudRepository;
    private final ProveedorRepository proveedorRepository;
    private final SeccionRepository seccionRepository;
    private final ProductoMapper productoMapper;

    // ==========================================
    // === PRODUCTO MASTER (Catálogo Base)    ===
    // ==========================================

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
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
        master.setCategoria(cat);
        return productoMapper.toResponse(productoMasterRepository.save(master));
    }

    @Transactional
    public void eliminarMaster(Integer id) {
        productoMasterRepository.findById(id).ifPresent(m -> {
            m.setActivo(false);
            productoMasterRepository.save(m);
        });
    }

    // ==========================================
    // === PRODUCTO (Stock / SKU Específico) ===
    // ==========================================

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerStockPorTienda(Integer codti) {
        return productoRepository.findByTienda_CodtiAndActivoTrue(codti).stream()
                .map(productoMapper::toProductoResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerStockDisponiblePorTienda(Integer codti) {
        return productoRepository.findAvailableStockByTienda(codti).stream()
                .map(productoMapper::toProductoResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductoResponseDTO crearProducto(ProductoRequestDTO dto) {
        Producto producto = productoMapper.toProductoEntity(dto);
        
        // Relaciones obligatorias
        ProductoMaster master = productoMasterRepository.findById(Math.toIntExact(dto.getIdProductoMaster()))
                .orElseThrow(() -> new RuntimeException("Producto Master no encontrado"));
        Tienda tienda = tiendaRepository.findById(1) // TODO: Context or Parameter
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada"));
        Magnitud mag = magnitudRepository.findById(dto.getIdMagnitud())
                .orElseThrow(() -> new RuntimeException("Magnitud no encontrada"));
        
        producto.setProductoMaster(master);
        producto.setTienda(tienda);
        producto.setMagnitud(mag);

        // Opcionales
        if (dto.getIdColor() != null) 
            producto.setColor(colorRepository.findById(dto.getIdColor()).orElse(null));
        if (dto.getIdProveedor() != null) 
            producto.setProveedor(proveedorRepository.findById(dto.getIdProveedor()).orElse(null));
        if (dto.getIdSeccion() != null) 
            producto.setSeccion(seccionRepository.findById(dto.getIdSeccion()).orElse(null));

        return productoMapper.toProductoResponse(productoRepository.save(producto));
    }

    @Transactional
    public void eliminarProducto(String codpro) {
        productoRepository.findByCodpro(codpro).ifPresent(p -> {
            p.setActivo(false);
            productoRepository.save(p);
        });
    }
}
