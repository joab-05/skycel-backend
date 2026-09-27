package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Integer> {

    List<Compra> findByEstadoNotOrderByFechaDesc(Byte estado);

    List<Compra> findByTienda_CodtiAndEstadoNotOrderByFechaDesc(Integer codti, Byte estado);

    List<Compra> findAllByOrderByFechaDesc();

    List<Compra> findByTienda_CodtiOrderByFechaDesc(Integer codti);

    @Query("SELECT COALESCE(SUM(c.montoTotal - c.montoPagado), 0) FROM Compra c WHERE c.estado != 2")
    BigDecimal totalPendiente();

    @Query("SELECT COALESCE(SUM(c.montoTotal - c.montoPagado), 0) FROM Compra c WHERE c.estado = 3")
    BigDecimal totalVencido();

    /** Compras que deben pasar a Vencida. */
    List<Compra> findByFechaVencimientoBeforeAndEstadoIn(LocalDate fecha, List<Byte> estados);
}
