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

    @Query("SELECT m FROM MovimientoInventario m LEFT JOIN m.producto p WHERE m.codti = :codti " +
            "AND m.fecha BETWEEN :desde AND :hasta " +
            "AND (:codpro IS NULL OR p.codpro = :codpro OR m.imei = :codpro) " +
            "ORDER BY m.fecha DESC, m.idmov DESC")
    List<MovimientoInventario> buscar(@Param("codti") Integer codti,
                                      @Param("desde") LocalDateTime desde,
                                      @Param("hasta") LocalDateTime hasta,
                                      @Param("codpro") String codpro);

    /** Salidas y ajustes (no ventas, devoluciones ni traspasos) de una tienda, o de todas si {@code codti} es null. */
    @Query("SELECT m FROM MovimientoInventario m LEFT JOIN FETCH m.producto p LEFT JOIN FETCH m.productoMaster pm LEFT JOIN FETCH m.usuario " +
            "WHERE (:codti IS NULL OR m.codti = :codti) AND m.tipo IN :tipos AND m.fecha BETWEEN :desde AND :hasta " +
            "ORDER BY m.fecha DESC, m.idmov DESC")
    List<MovimientoInventario> mermas(@Param("codti") Integer codti, @Param("tipos") List<String> tipos,
                                      @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
