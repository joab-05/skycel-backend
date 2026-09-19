// ── CuentaPorCobrarRepository.java ───────────────────────────────────────────
package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.CuentaPorCobrar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CuentaPorCobrarRepository extends JpaRepository<CuentaPorCobrar, Integer> {

    List<CuentaPorCobrar> findByEstadoNot(Byte estado);

    @Query("SELECT COALESCE(SUM(c.montoTotal - c.montoPagado), 0) FROM CuentaPorCobrar c WHERE c.estado != 2")
    BigDecimal totalPendiente();

    @Query("SELECT COALESCE(SUM(c.montoTotal - c.montoPagado), 0) FROM CuentaPorCobrar c WHERE c.estado = 3")
    BigDecimal totalVencido();

    boolean existsByNoFactura(String noFactura);

    /** Cuenta generada por una venta a crédito (una venta tiene, como máximo, una). */
    java.util.Optional<CuentaPorCobrar> findByVenta_Idventa(Integer idventa);

    /** Cuentas que deben pasar a Vencido */
    List<CuentaPorCobrar> findByFechaVencimientoBeforeAndEstadoIn(LocalDate fecha, List<Byte> estados);
}