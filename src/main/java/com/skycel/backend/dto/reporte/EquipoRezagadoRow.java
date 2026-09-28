package com.skycel.backend.dto.reporte;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una unidad (IMEI) disponible detectada como rezagada: vieja por fecha o marcada a mano (proyección JPQL). */
@Getter
public class EquipoRezagadoRow {

    private final Long idProductoImei;
    private final String imei;
    private final Integer codti;
    private final String nombreTienda;
    private final String codpro;
    private final String nombreArticulo;
    private final String condicion;
    private final LocalDateTime fechaRegistro;
    private final BigDecimal precio;
    private final BigDecimal costo;
    private final boolean marcadoManual;

    public EquipoRezagadoRow(Long idProductoImei, String imei, Integer codti, String nombreTienda, String codpro,
                              String nombreArticulo, String condicion, LocalDateTime fechaRegistro,
                              BigDecimal precio, BigDecimal costo, Boolean marcadoManual) {
        this.idProductoImei = idProductoImei;
        this.imei = imei;
        this.codti = codti;
        this.nombreTienda = nombreTienda;
        this.codpro = codpro;
        this.nombreArticulo = nombreArticulo;
        this.condicion = condicion;
        this.fechaRegistro = fechaRegistro;
        this.precio = precio != null ? precio : BigDecimal.ZERO;
        this.costo = costo != null ? costo : BigDecimal.ZERO;
        this.marcadoManual = Boolean.TRUE.equals(marcadoManual);
    }
}
