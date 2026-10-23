package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DevolucionRepository extends JpaRepository<Devolucion, Integer> {

    Optional<Devolucion> findByFolio(String folio);

    /** Con tienda y estado opcionales, las más recientes primero. */
    @Query("SELECT d FROM Devolucion d WHERE (:codti IS NULL OR d.tienda.codti = :codti) " +
            "AND (:estado IS NULL OR d.estado = :estado) ORDER BY d.fechaSolicitud DESC, d.iddevolucion DESC")
    List<Devolucion> buscar(@Param("codti") Integer codti, @Param("estado") Byte estado);

    /** ¿La venta tiene devoluciones que siguen su curso o ya se procesaron? (no se puede cancelar). */
    @Query("SELECT COUNT(d) > 0 FROM Devolucion d WHERE d.venta.idventa = :idventa AND d.estado IN (1, 2)")
    boolean existeVigenteDeVenta(@Param("idventa") Integer idventa);

    List<Devolucion> findByVenta_IdventaOrderByIddevolucion(Integer idventa);

    /** Lo devuelto a clientes (reembolsos y cambios ya procesados) en el rango, de las sucursales dadas. */
    @Query("""
            SELECT COALESCE(SUM(d.totalDevuelto), 0) FROM Devolucion d
            WHERE d.estado = 2 AND d.fechaResolucion BETWEEN :desde AND :hasta AND d.tienda.codti IN :codtis
            """)
    BigDecimal totalDevuelto(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                             @Param("codtis") List<Integer> codtis);

    /** Lo que costó la mercancía que volvió a existencias en esas devoluciones (se resta del costo de lo vendido). */
    @Query("""
            SELECT COALESCE(SUM(dd.cantidad * vd.costoUnitarioCompra), 0)
            FROM DevolucionDetalle dd, Devolucion d, VentaDetalle vd
            WHERE dd.devolucion = d AND vd.iddetalleVenta = dd.iddetalleVenta AND dd.reingresa = true
              AND d.estado = 2 AND d.fechaResolucion BETWEEN :desde AND :hasta AND d.tienda.codti IN :codtis
            """)
    BigDecimal costoReingresado(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                @Param("codtis") List<Integer> codtis);
}
