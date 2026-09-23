package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    /** Un mismo artículo lleva el mismo código en todas las sucursales, así que un código puede dar varias filas. */
    List<Producto> findAllByCodpro(String codpro);

    Optional<Producto> findByCodproAndTienda_Codti(String codpro, Integer codti);

    /** Todas las filas (una por sucursal) de un artículo maestro. */
    List<Producto> findByProductoMaster_IdprodmasterOrderByIdproductoAsc(Integer idprodmaster);

    List<Producto> findByTienda_CodtiAndActivoTrue(Integer codti);
    
    List<Producto> findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(Integer idprodmaster, Integer codti);

    @Query("SELECT p FROM Producto p WHERE p.tienda.codti = :codti AND p.stock > 0 AND p.activo = true")
    List<Producto> findAvailableStockByTienda(Integer codti);

    /** Productos (no servicios) que llegaron a su umbral de reposición: stock <= stockMinimo, con umbral definido. */
    @Query("SELECT p FROM Producto p WHERE p.tienda.codti = :codti AND p.activo = true " +
           "AND p.productoMaster.tipo <> com.skycel.backend.domain.enums.TipoProducto.SERVICIO " +
           "AND p.stockMinimo > 0 AND p.stock <= p.stockMinimo " +
           "ORDER BY p.stock ASC, p.codpro ASC")
    List<Producto> findBajoStockByTienda(Integer codti);

    /** Accesorios de la tienda compatibles con un modelo (o "Universal"), con stock. */
    @Query("SELECT p FROM Producto p WHERE p.tienda.codti = :codti AND p.activo = true AND p.stock > 0 " +
           "AND p.productoMaster.tipo = com.skycel.backend.domain.enums.TipoProducto.ACCESORIO " +
           "AND (LOWER(p.productoMaster.compatibilidad) LIKE LOWER(CONCAT('%', :modelo, '%')) " +
           "     OR LOWER(p.productoMaster.compatibilidad) LIKE '%universal%') " +
           "ORDER BY p.productoMaster.nombreBase ASC")
    List<Producto> findAccesoriosCompatibles(Integer codti, String modelo);
}
