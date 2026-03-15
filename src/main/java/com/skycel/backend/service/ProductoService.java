package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.repository.ProductoMasterRepository;
import com.skycel.backend.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoMasterRepository productoMasterRepository;
    private final ProductoRepository productoRepository;

    // --- Producto Master Operations ---

    @Transactional(readOnly = true)
    public List<ProductoMaster> obtenerTodosLosProductosMaster() {
        return productoMasterRepository.findByActivoTrue();
    }
    
    @Transactional(readOnly = true)
    public Optional<ProductoMaster> obtenerProductoMasterPorId(Integer id) {
        return productoMasterRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ProductoMaster> buscarProductoMaster(String parametroBusqueda) {
        return productoMasterRepository.searchByKeyword(parametroBusqueda);
    }

    // --- Inventario/Producto Operations ---

    @Transactional(readOnly = true)
    public List<Producto> obtenerInventarioPorTienda(Integer codti) {
        return productoRepository.findByTienda_CodtiAndActivoTrue(codti);
    }

    @Transactional(readOnly = true)
    public List<Producto> obtenerStockDisponiblePorTienda(Integer codti) {
        return productoRepository.findAvailableStockByTienda(codti);
    }

    @Transactional(readOnly = true)
    public Optional<Producto> buscarPorCodigoYTienda(String codpro, Integer codti) {
        return productoRepository.findByCodproAndTienda_Codti(codpro, codti);
    }

    @Transactional(readOnly = true)
    public List<Producto> obtenerInventarioEspecifico(Integer idprodmaster, Integer codti) {
         return productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(idprodmaster, codti);
    }

    @Transactional
    public Producto guardarProducto(Producto producto) {
        // Here we could add logic to link auditing / usuarioModificador
        return productoRepository.save(producto);
    }
}
