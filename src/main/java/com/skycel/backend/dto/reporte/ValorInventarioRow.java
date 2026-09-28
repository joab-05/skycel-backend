package com.skycel.backend.dto.reporte;

import lombok.Getter;

import java.math.BigDecimal;

/** El valor del inventario activo de una sucursal, a costo y a precio de venta (proyección JPQL). */
@Getter
public class ValorInventarioRow {

    private final Integer codti;
    private final String nombreTienda;
    private final BigDecimal valorCosto;
    private final BigDecimal valorVenta;
    private final Long numProductos;

    public ValorInventarioRow(Integer codti, String nombreTienda, BigDecimal valorCosto, BigDecimal valorVenta, Long numProductos) {
        this.codti = codti;
        this.nombreTienda = nombreTienda;
        this.valorCosto = valorCosto != null ? valorCosto : BigDecimal.ZERO;
        this.valorVenta = valorVenta != null ? valorVenta : BigDecimal.ZERO;
        this.numProductos = numProductos;
    }
}
