package com.skycel.backend.dto.venta;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class VentaDetalleResponseDto {

    private Integer iddetalleVenta;
    private Integer idprodmaster;
    private String nombreProducto;
    private String codpro;
    private Short cantidad;
    private BigDecimal precioUnitarioBase;
    private BigDecimal precioUnitarioFinal;
    private BigDecimal costoUnitarioCompra;
    private BigDecimal subtotal;
    private String imei;
    private Boolean esRegalo;
}