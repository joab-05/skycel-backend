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

    /** Cuándo salió por última vez en una venta cada uno de estos productos (id del producto, fecha). */
    @Query("SELECT m.producto.idproducto, MAX(m.fecha) FROM MovimientoInventario m "
            + "WHERE m.tipo = 'VENTA' AND m.producto.idproducto IN :ids GROUP BY m.producto.idproducto")
    List<Object[]> ultimaVentaPorProducto(@org.springframework.data.repository.query.Param("ids") List<Integer> ids);

    @Query("SELECT m FROM MovimientoInventario m LEFT JOIN m.producto p WHERE m.codti = :codti " +
            "AND m.fecha BETWEEN :desde AND :hasta " +
            "AND (:codpro IS NULL OR p.codpro = :codpro OR m.imei = :codpro) " +
            "ORDER BY m.fecha DESC, m.idmov DESC")
    List<MovimientoInventario> buscar(@Param("codti") Integer codti,
                                      @Param("desde") LocalDateTime desde,
                                      @Param("hasta") LocalDateTime hasta,
                                      @Param("codpro") String codpro);

    /** Todo lo que le ha pasado a un equipo (por su IMEI o serie), del más antiguo al más reciente. */
    @Query("SELECT m FROM MovimientoInventario m LEFT JOIN FETCH m.usuario WHERE m.imei = :imei ORDER BY m.fecha ASC, m.idmov ASC")
    List<MovimientoInventario> rastro(@Param("imei") String imei);

    /** Salidas y ajustes (no ventas, devoluciones ni traspasos) de una tienda, o de todas si {@code codti} es null. */
    @Query("SELECT m FROM MovimientoInventario m LEFT JOIN FETCH m.producto p LEFT JOIN FETCH m.productoMaster pm LEFT JOIN FETCH m.usuario " +
            "WHERE (:codti IS NULL OR m.codti = :codti) AND m.tipo IN :tipos AND m.fecha BETWEEN :desde AND :hasta " +
            "ORDER BY m.fecha DESC, m.idmov DESC")
    List<MovimientoInventario> mermas(@Param("codti") Integer codti, @Param("tipos") List<String> tipos,
                                      @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
