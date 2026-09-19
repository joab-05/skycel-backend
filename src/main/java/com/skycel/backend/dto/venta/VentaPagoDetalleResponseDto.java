package com.skycel.backend.dto.venta;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class VentaPagoDetalleResponseDto {
    private Byte       metodoPago;
    private String     descripcionMetodoPago;
    private BigDecimal monto;
    private String     folioOperacion;
}
