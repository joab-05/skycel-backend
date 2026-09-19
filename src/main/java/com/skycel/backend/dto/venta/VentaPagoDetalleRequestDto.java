package com.skycel.backend.dto.venta;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Una línea del desglose de una venta con pago Mixto. */
@Data
public class VentaPagoDetalleRequestDto {

    /** 1 = Efectivo, 2 = Tarjeta, 3 = Transferencia, 5 = PayJoy */
    @NotNull(message = "El método de pago de cada línea es obligatorio")
    private Byte metodoPago;

    @NotNull(message = "El monto de cada línea es obligatorio")
    @Positive(message = "El monto de cada línea debe ser mayor a 0")
    private BigDecimal monto;

    /** Folio del voucher de la terminal o número de rastreo de la transferencia (opcional). */
    @Size(max = 50, message = "El folio no puede exceder los 50 caracteres")
    private String folioOperacion;
}
