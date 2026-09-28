package com.skycel.backend.dto.reporte;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Una fila del comparativo de ventas por sucursal (proyección JPQL desde VentaDetalle). */
@Getter
public class ResumenTiendaRow {

    private final Integer codti;
    private final String nombreTienda;
    private final Long numVentas;
    private final BigDecimal totalVenta;
    private final BigDecimal totalCosto;

    public ResumenTiendaRow(Integer codti, String nombreTienda, Long numVentas, BigDecimal totalVenta, BigDecimal totalCosto) {
        this.codti = codti;
        this.nombreTienda = nombreTienda;
        this.numVentas = numVentas;
        this.totalVenta = totalVenta != null ? totalVenta : BigDecimal.ZERO;
        this.totalCosto = totalCosto != null ? totalCosto : BigDecimal.ZERO;
    }

    public BigDecimal getUtilidad() {
        return totalVenta.subtract(totalCosto);
    }

    /** Margen sobre venta, en porcentaje (0-100). 0 si no hubo ventas, para no dividir entre cero. */
    public BigDecimal getMargenPorciento() {
        if (totalVenta.signum() == 0) return BigDecimal.ZERO;
        return getUtilidad().multiply(BigDecimal.valueOf(100)).divide(totalVenta, 1, RoundingMode.HALF_UP);
    }
}
