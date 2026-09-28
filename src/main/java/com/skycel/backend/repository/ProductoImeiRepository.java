package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ProductoImei;
import com.skycel.backend.dto.reporte.EquipoRezagadoRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoImeiRepository extends JpaRepository<ProductoImei, Long> {

    Optional<ProductoImei> findByImei(String imei);

    List<ProductoImei> findByProducto_IdproductoAndEstado(Integer idproducto, String estado);

    /** Las unidades en ese estado de varios productos a la vez (una consulta en vez de una por producto). */
    List<ProductoImei> findByProducto_IdproductoInAndEstado(java.util.Collection<Integer> idproductos, String estado);

    boolean existsByImei(String imei);

    /** Unidades disponibles rezagadas: llevan en existencia desde antes de {@code limite}, o el artículo está
     *  marcado a mano como rezagado ({@code producto.rezagado}), en cualquiera de las sucursales dadas. */
    @Query("""
            SELECT new com.skycel.backend.dto.reporte.EquipoRezagadoRow(
                pi.id, pi.imei, p.tienda.codti, p.tienda.nombre, p.codpro, pm.nombreBase, pi.condicion,
                pi.fechaRegistro, COALESCE(pi.precioVentaOverride, p.preciopub), COALESCE(pi.costoUnitario, p.preciopro),
                p.rezagado)
            FROM ProductoImei pi JOIN pi.producto p JOIN p.productoMaster pm
            WHERE pi.estado = 'DISPONIBLE' AND p.tienda.codti IN :codtis
              AND (pi.fechaRegistro <= :limite OR p.rezagado = true)
            ORDER BY pi.fechaRegistro ASC
            """)
    List<EquipoRezagadoRow> rezagados(@Param("limite") LocalDateTime limite, @Param("codtis") List<Integer> codtis);
}
