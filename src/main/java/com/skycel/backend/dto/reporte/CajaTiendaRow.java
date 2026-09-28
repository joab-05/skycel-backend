package com.skycel.backend.dto.reporte;

import lombok.Getter;

import java.math.BigDecimal;

/** Totales de caja de una sucursal (todas sus cajas juntas), histórico y de hoy (proyección JPQL). */
@Getter
public class CajaTiendaRow {

    private final Integer codti;
    private final String nombreTienda;
    private final Long numCajas;
    private final BigDecimal entradasTotal;
    private final BigDecimal salidasTotal;
    private final BigDecimal entradasHoy;
    private final BigDecimal salidasHoy;

    public CajaTiendaRow(Integer codti, String nombreTienda, Long numCajas,
                          BigDecimal entradasTotal, BigDecimal salidasTotal,
                          BigDecimal entradasHoy, BigDecimal salidasHoy) {
        this.codti = codti;
        this.nombreTienda = nombreTienda;
        this.numCajas = numCajas != null ? numCajas : 0L;
        this.entradasTotal = entradasTotal != null ? entradasTotal : BigDecimal.ZERO;
        this.salidasTotal = salidasTotal != null ? salidasTotal : BigDecimal.ZERO;
        this.entradasHoy = entradasHoy != null ? entradasHoy : BigDecimal.ZERO;
        this.salidasHoy = salidasHoy != null ? salidasHoy : BigDecimal.ZERO;
    }

    public BigDecimal getSaldo() {
        return entradasTotal.subtract(salidasTotal);
    }

    public BigDecimal getSaldoHoy() {
        return entradasHoy.subtract(salidasHoy);
    }
}
