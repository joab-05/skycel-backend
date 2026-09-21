package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.CorteServicios;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CorteServiciosRepository extends JpaRepository<CorteServicios, Integer> {

    Optional<CorteServicios> findByTienda_CodtiAndFecha(Integer codti, LocalDate fecha);

    @Query("SELECT c FROM CorteServicios c WHERE (:codti IS NULL OR c.tienda.codti = :codti) " +
            "AND c.fecha BETWEEN :desde AND :hasta AND (:estado IS NULL OR c.estado = :estado) " +
            "ORDER BY c.fecha DESC, c.tienda.codti")
    List<CorteServicios> buscar(@Param("codti") Integer codti, @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta, @Param("estado") Byte estado);
}
