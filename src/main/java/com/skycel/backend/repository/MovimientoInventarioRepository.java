package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    @Query("SELECT m FROM MovimientoInventario m WHERE m.producto.tienda.codti = :codti " +
            "AND m.fecha BETWEEN :desde AND :hasta " +
            "AND (:codpro IS NULL OR m.producto.codpro = :codpro) " +
            "ORDER BY m.fecha DESC, m.idmov DESC")
    List<MovimientoInventario> buscar(@Param("codti") Integer codti,
                                      @Param("desde") LocalDateTime desde,
                                      @Param("hasta") LocalDateTime hasta,
                                      @Param("codpro") String codpro);
}
