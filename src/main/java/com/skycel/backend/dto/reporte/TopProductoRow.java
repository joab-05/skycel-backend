package com.skycel.backend.dto.reporte;

import lombok.Getter;

import java.math.BigDecimal;

/** Una fila del ranking de productos más vendidos, por cantidad o por monto (proyección JPQL). */
@Getter
public class TopProductoRow {

    private final Integer idprodmaster;
    private final String nombreProducto;
    private final Long unidades;
    private final BigDecimal montoVenta;

    public TopProductoRow(Integer idprodmaster, String nombreProducto, Long unidades, BigDecimal montoVenta) {
        this.idprodmaster = idprodmaster;
        this.nombreProducto = nombreProducto;
        this.unidades = unidades;
        this.montoVenta = montoVenta != null ? montoVenta : BigDecimal.ZERO;
    }
}
