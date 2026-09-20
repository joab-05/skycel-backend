package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
