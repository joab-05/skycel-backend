package com.skycel.backend.dto.comision;

import lombok.Getter;

import java.math.BigDecimal;

/** Una línea de equipo vendida a crédito PayJoy (proyección JPQL desde VentaDetalle), para aplicarle su tramo. */
@Getter
public class VentaPayjoyLineaRow {

    private final Integer idusuarioVendedor;
    private final String nombreVendedor;
    private final Integer iddetalleVenta;
    private final BigDecimal precioUnitarioFinal;

    public VentaPayjoyLineaRow(Integer idusuarioVendedor, String nombreVendedor, Integer iddetalleVenta, BigDecimal precioUnitarioFinal) {
        this.idusuarioVendedor = idusuarioVendedor;
        this.nombreVendedor = nombreVendedor;
        this.iddetalleVenta = iddetalleVenta;
        this.precioUnitarioFinal = precioUnitarioFinal;
    }
}
