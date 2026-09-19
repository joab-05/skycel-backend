package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Integer> {

    /** Último movimiento registrado en una caja (para saber si está abierta y desde cuándo) */
    Optional<MovimientoCaja> findTopByCaja_IdCajaOrderByFechaMovDesc(Integer idCaja);

    /** Movimientos de una caja desde una fecha/hora en adelante (turno actual), en orden cronológico */
    List<MovimientoCaja> findByCaja_IdCajaAndFechaMovGreaterThanEqualOrderByFechaMovAsc(Integer idCaja, LocalDateTime desde);

    List<MovimientoCaja> findByCaja_IdCajaOrderByFechaMovDesc(Integer idCaja);

    List<MovimientoCaja> findByCaja_IdCajaAndFechaMovBetweenOrderByFechaMovDesc(
            Integer idCaja, LocalDateTime desde, LocalDateTime hasta);

    /** Movimiento automático generado por una operación (ej. observaciones = "Venta #12"). */
    Optional<MovimientoCaja> findFirstByMotivo_IdmotivoAndObservaciones(Integer idmotivo, String observaciones);

    /** Suma de los movimientos de una caja de un tipo (1 = entradas, 2 = salidas). */
    @Query("SELECT COALESCE(SUM(m.monto), 0) FROM MovimientoCaja m " +
           "WHERE m.caja.idCaja = :idCaja AND m.tipo = :tipo")
    BigDecimal totalPorTipo(@Param("idCaja") Integer idCaja, @Param("tipo") Byte tipo);
}