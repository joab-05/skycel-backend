package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.dto.reporte.ValorInventarioRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    /** Un mismo artículo lleva el mismo código en todas las sucursales, así que un código puede dar varias filas. */
    List<Producto> findAllByCodpro(String codpro);

    Optional<Producto> findByCodproAndTienda_Codti(String codpro, Integer codti);

    long countByColor_IdcolorAndActivoTrue(Short idcolor);

    long countByProveedor_IdproveedorAndActivoTrue(Short idproveedor);

    /** Códigos de artículo que empiezan con ese prefijo ("CEL" → CEL-000001...). */
    @Query("SELECT p.codpro FROM Producto p WHERE p.codpro LIKE CONCAT(:prefijo, '-%')")
    List<String> codigosConPrefijo(@Param("prefijo") String prefijo);

    /** Varios códigos de una tienda a la vez (para resolver nombres en lote, sin una consulta por código). */
    List<Producto> findByCodproInAndTienda_Codti(java.util.Collection<String> codpros, Integer codti);

    /** Todas las filas (una por sucursal) de un artículo maestro. */
    List<Producto> findByProductoMaster_IdprodmasterOrderByIdproductoAsc(Integer idprodmaster);

    List<Producto> findByTienda_CodtiAndActivoTrue(Integer codti);

    /** Los productos activos de una sección de una sucursal (para el inventario físico). */
    List<Producto> findByTienda_CodtiAndSeccion_IdseccionAndActivoTrue(Integer codti, Short idseccion);

    /**
     * Los productos activos de una tienda con TODO lo que el DTO necesita (tienda, artículo, categoría, color,
     * proveedor, magnitud y sección) en una sola consulta. Sin esto, listar ~2,000 productos hacía ~2,000 consultas
     * extra (una por relación perezosa de cada producto) y tardaba varios segundos.
     */
    @Query("SELECT p FROM Producto p JOIN FETCH p.tienda LEFT JOIN FETCH p.productoMaster m LEFT JOIN FETCH m.categoria " +
           "LEFT JOIN FETCH p.color LEFT JOIN FETCH p.proveedor LEFT JOIN FETCH p.magnitud LEFT JOIN FETCH p.seccion " +
           "WHERE p.tienda.codti = :codti AND p.activo = true")
    List<Producto> findActivosConDetalleByTienda(Integer codti);

    /** Igual que {@link #findActivosConDetalleByTienda} pero solo con stock (lo que el punto de venta puede vender). */
    @Query("SELECT p FROM Producto p JOIN FETCH p.tienda LEFT JOIN FETCH p.productoMaster m LEFT JOIN FETCH m.categoria " +
           "LEFT JOIN FETCH p.color LEFT JOIN FETCH p.proveedor LEFT JOIN FETCH p.magnitud LEFT JOIN FETCH p.seccion " +
           "WHERE p.tienda.codti = :codti AND p.stock > 0 AND p.activo = true")
    List<Producto> findDisponiblesConDetalleByTienda(Integer codti);
    
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

    /**
     * Valor del inventario activo (sin servicios, que no manejan stock) por sucursal: a costo y a precio de
     * venta. Una sucursal sin productos activos simplemente no aparece.
     */
    @Query("""
            SELECT new com.skycel.backend.dto.reporte.ValorInventarioRow(
                p.tienda.codti, p.tienda.nombre,
                COALESCE(SUM(p.stock * p.preciopro), 0), COALESCE(SUM(p.stock * p.preciopub), 0), COUNT(p))
            FROM Producto p
            WHERE p.activo = true AND p.tienda.codti IN :codtis
              AND p.productoMaster.tipo <> com.skycel.backend.domain.enums.TipoProducto.SERVICIO
            GROUP BY p.tienda.codti, p.tienda.nombre
            """)
    List<ValorInventarioRow> valorInventarioPorTienda(@Param("codtis") List<Integer> codtis);

    /**
     * Accesorios con existencia que llevan sin venderse desde {@code limite} (y que ya estaban en la tienda antes de
     * esa fecha, para no marcar lo recién recibido). Los más caros de tener parados (a costo) primero.
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.activo = true AND p.stock > 0 AND p.tienda.codti IN :codtis
              AND p.productoMaster.tipo = com.skycel.backend.domain.enums.TipoProducto.ACCESORIO
              AND (p.fechaIngreso IS NULL OR p.fechaIngreso < :limite)
              AND NOT EXISTS (SELECT 1 FROM VentaDetalle d JOIN d.venta v
                              WHERE d.codpro = p.codpro AND v.tienda = p.tienda AND v.estado = 1 AND v.fechaVenta >= :limite)
            ORDER BY p.stock * p.preciopro DESC
            """)
    List<Producto> sinMovimiento(@Param("limite") java.time.LocalDateTime limite, @Param("codtis") List<Integer> codtis,
                                 org.springframework.data.domain.Pageable pagina);
}
