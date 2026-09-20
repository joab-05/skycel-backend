package com.skycel.backend.dto.garantia;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/** Respuesta a "¿este producto de esta venta todavía tiene garantía?". */
@Data
@Builder
public class ElegibilidadGarantiaResponseDto {
    private Boolean   elegible;
    /** Cuando no es elegible, la razón (venta cancelada, garantía vencida, sin garantía definida...). */
    private String    motivo;
    private String    nombreProducto;
    private Integer   diasGarantia;
    private LocalDate fechaVenta;
    private LocalDate garantiaVigenteHasta;
    /** Días naturales que le quedan a la garantía (0 el último día). */
    private Long      diasRestantes;
}
