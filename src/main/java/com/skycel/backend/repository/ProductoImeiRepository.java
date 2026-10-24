package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ProductoImei;
import com.skycel.backend.dto.reporte.EquipoRezagadoRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoImeiRepository extends JpaRepository<ProductoImei, Long> {

    Optional<ProductoImei> findByImei(String imei);

    boolean existsByImei(String imei);

    /** Cuántas unidades de un artículo hay en esa sucursal con ese estado (las existencias son las DISPONIBLES). */
    long countByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(Integer idprodmaster, Integer codti, String estado);

    /** Las unidades de una sucursal en esos estados, con su artículo (una sola consulta). */
    @Query("""
            SELECT pi FROM ProductoImei pi JOIN FETCH pi.productoMaster pm LEFT JOIN FETCH pm.categoria
            WHERE pi.tienda.codti = :codti AND pi.estado IN :estados
            ORDER BY pm.nombreBase ASC, pi.fechaRegistro ASC
            """)
    List<ProductoImei> deLaSucursal(@Param("codti") Integer codti, @Param("estados") Collection<String> estados);

    /** Las unidades disponibles de un artículo en una sucursal. */
    List<ProductoImei> findByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(Integer idprodmaster, Integer codti, String estado);

    /** Las unidades de un artículo en ese estado, de todas las sucursales. */
    List<ProductoImei> findByProductoMaster_IdprodmasterAndEstado(Integer idprodmaster, String estado);

    /** Valor de las unidades disponibles por sucursal: a costo y a precio de venta. Filas: codti, nombre, unidades, costo, precio. */
    @Query("""
            SELECT pi.tienda.codti, pi.tienda.nombre, COUNT(pi), COALESCE(SUM(pi.costo), 0), COALESCE(SUM(pi.precioVenta), 0)
            FROM ProductoImei pi
            WHERE pi.estado = 'DISPONIBLE' AND pi.tienda.codti IN :codtis
            GROUP BY pi.tienda.codti, pi.tienda.nombre
            """)
    List<Object[]> valorPorTienda(@Param("codtis") List<Integer> codtis);

    /** Unidades disponibles rezagadas: llevan en existencia desde antes de {@code limite}, o se marcaron a mano como rezagadas. */
    @Query("""
            SELECT new com.skycel.backend.dto.reporte.EquipoRezagadoRow(
                pi.id, pi.imei, pi.tienda.codti, pi.tienda.nombre, '', pm.nombreBase, pi.condicion,
                pi.fechaRegistro, pi.precioVenta, pi.costo, pi.rezagado)
            FROM ProductoImei pi JOIN pi.productoMaster pm
            WHERE pi.estado = 'DISPONIBLE' AND pi.tienda.codti IN :codtis
              AND (pi.fechaRegistro <= :limite OR pi.rezagado = true)
            ORDER BY pi.fechaRegistro ASC
            """)
    List<EquipoRezagadoRow> rezagados(@Param("limite") LocalDateTime limite, @Param("codtis") List<Integer> codtis);

    /** Unidades (de cualquier estado) cuyo IMEI o serie contiene el texto: sirve para buscar en el punto de venta. */
    @Query("""
            SELECT pi FROM ProductoImei pi JOIN FETCH pi.productoMaster pm
            WHERE pi.tienda.codti = :codti AND pi.estado = 'DISPONIBLE'
              AND (pi.imei LIKE CONCAT('%', :texto, '%') OR LOWER(pm.nombreBase) LIKE LOWER(CONCAT('%', :texto, '%')))
            ORDER BY pm.nombreBase ASC, pi.fechaRegistro ASC
            """)
    List<ProductoImei> buscarDisponibles(@Param("codti") Integer codti, @Param("texto") String texto);
}
