package com.skycel.backend.dto.venta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class VentaRequestDto {

    @NotNull(message = "La tienda es obligatoria")
    private Integer codti;

    @NotNull(message = "La caja es obligatoria")
    private Integer idCaja;

    /** Cliente opcional (venta de mostrador sin cliente registrado = null) */
    private Integer idcliente;

    /**
     * Tipo de comprobante:
     * 1 = Ticket, 2 = Factura
     */
    @NotNull
    private Byte tipoComprobante = 1;

    /**
     * Método de pago:
     * 1 = Efectivo, 2 = Tarjeta, 3 = Transferencia, 4 = Mixto, 5 = PayJoy
     */
    @NotNull(message = "El método de pago es obligatorio")
    private Byte metodoPago;

    /** Monto entregado por el cliente (para calcular cambio) */
    private BigDecimal montoAbonado;

    private String observaciones;

    /**
     * Solo aplica a ventas a crédito (montoAbonado menor al total): fecha límite para liquidar
     * el saldo. Si no se indica, son 30 días desde hoy.
     */
    private LocalDate fechaVencimientoCredito;

    @NotEmpty(message = "La venta debe tener al menos un artículo")
    @Valid
    private List<VentaDetalleRequestDto> detalles;
}