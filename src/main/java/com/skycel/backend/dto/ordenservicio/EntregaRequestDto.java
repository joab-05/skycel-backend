package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Entrega del equipo y cobro del saldo. Genera la venta de la orden. */
@Data
public class EntregaRequestDto {

    /** Caja donde se cobra. Opcional: si se omite, la principal de la tienda de la orden. */
    private Integer idCaja;

    /**
     * Cómo paga el saldo: 1 Efectivo, 2 Tarjeta, 3 Transferencia o 5 PayJoy. Obligatorio si queda saldo por
     * cobrar (si el anticipo cubrió todo el total, no hace falta).
     */
    private Byte metodoPago;

    /** Folio del voucher o número de rastreo del pago del saldo (opcional). */
    @Size(max = 50, message = "El folio no puede exceder los 50 caracteres")
    private String folioOperacion;

    /**
     * Solo si la orden no tiene anticipos: lo que entrega el cliente (para calcular el cambio). Si es menor al
     * total, lo restante queda a crédito.
     */
    private BigDecimal montoRecibido;

    private String observaciones;
}
