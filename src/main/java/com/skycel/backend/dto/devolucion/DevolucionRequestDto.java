package com.skycel.backend.dto.devolucion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Solicitud de devolución de productos de una venta. */
@Data
public class DevolucionRequestDto {

    @NotNull(message = "La venta es obligatoria")
    private Integer idventa;

    /** 1 = Reembolso (se devuelve el dinero), 2 = Cambio (se lleva otro producto de igual o mayor valor). */
    @NotNull(message = "El tipo de devolución es obligatorio")
    @Min(value = 1, message = "tipo debe ser 1 (Reembolso) o 2 (Cambio)")
    @Max(value = 2, message = "tipo debe ser 1 (Reembolso) o 2 (Cambio)")
    private Byte tipo;

    @NotBlank(message = "El motivo de la devolución es obligatorio")
    @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
    private String motivo;

    /** Reembolso: cómo se devuelve el dinero (1 Efectivo, 2 Tarjeta, 3 Transferencia). Solo el efectivo mueve la caja. */
    private Byte metodoReembolso;

    @Size(max = 50, message = "El folio no puede exceder los 50 caracteres")
    private String folioOperacion;

    /** Caja que se usa para el efectivo. Opcional: por defecto, la de la venta original. */
    private Integer idCaja;

    @NotEmpty(message = "Indique qué productos se devuelven")
    @Valid
    private List<LineaDevolucionDto> lineas;

    /** Solo en un cambio: lo que el cliente se lleva. */
    @Valid
    private CambioDto cambio;

    @Data
    public static class LineaDevolucionDto {
        /** El renglón de la venta original (ver GET /api/devoluciones/venta/{idventa}). */
        @NotNull(message = "El renglón de la venta es obligatorio")
        private Integer iddetalleVenta;
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad mínima es 1")
        private Short cantidad;
        /** false = el producto viene dañado y no regresa al inventario (se da de baja). Por defecto, true. */
        private Boolean reingresa = true;
    }

    @Data
    public static class CambioDto {
        @NotEmpty(message = "Indique qué producto se lleva el cliente a cambio")
        @Valid
        private List<LineaCambioDto> lineas;
        /** Cómo paga la diferencia si lo nuevo vale más (1 Efectivo, 2 Tarjeta, 3 Transferencia). */
        private Byte metodoPago;
    }

    @Data
    public static class LineaCambioDto {
        @NotNull(message = "El idprodmaster es obligatorio")
        private Integer idprodmaster;
        private String codpro;
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad mínima es 1")
        private Short cantidad;
        @NotNull(message = "El precio es obligatorio")
        @Positive(message = "El precio debe ser mayor a 0")
        private BigDecimal precioUnitarioFinal;
        /** Equipos: la unidad que se lleva. */
        private String imei;
    }
}
