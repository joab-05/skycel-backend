package com.skycel.backend.dto.descuento;

import lombok.Data;

import java.math.BigDecimal;

/** Lo que manda el punto de venta al pedir un descuento para una línea de la venta. */
@Data
public class SolicitudDescuentoRequestDto {
    private Integer codti;
    private Integer idprodmaster;
    private String imei;
    private String codpro;
    private Short cantidad;
    /** El precio de la línea al pedir (por pieza). */
    private BigDecimal precioLista;
    /** El descuento que se pide, por pieza. */
    private BigDecimal descuentoPorPieza;
    private String motivo;
}
