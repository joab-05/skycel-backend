package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AnticipoRequestDto {

    @NotNull(message = "El monto del anticipo es obligatorio")
    @Positive(message = "El monto del anticipo debe ser mayor a 0")
    private BigDecimal monto;

    /** 1 = Efectivo, 2 = Tarjeta, 3 = Transferencia. Solo el efectivo entra a la caja. */
    @NotNull(message = "El método de pago del anticipo es obligatorio")
    private Byte metodoPago;

    /** Folio del voucher o número de rastreo de la transferencia (opcional). */
    @Size(max = 50, message = "El folio no puede exceder los 50 caracteres")
    private String folioOperacion;

    /** Caja donde entra el efectivo. Opcional: si se omite, la principal de la tienda de la orden. */
    private Integer idCaja;
}
