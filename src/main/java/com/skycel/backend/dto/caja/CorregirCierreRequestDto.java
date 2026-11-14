package com.skycel.backend.dto.caja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Corrección de un corte que ya se cerró (solo un administrador): lo que se contó de verdad o lo que se dejó para mañana,
 * cuando quien cerró se equivocó. Siempre lleva el motivo y queda registrado quién lo corrigió y qué decía antes.
 */
@Data
public class CorregirCierreRequestDto {

    /** El efectivo que de verdad había en el cajón. */
    @NotNull(message = "El efectivo contado es obligatorio")
    @DecimalMin(value = "0.00", message = "El efectivo contado no puede ser negativo")
    private BigDecimal efectivoContado;

    /** Lo que se dejó para mañana. Si se omite se conserva el que tenía el corte (o lo contado, si es menos). */
    @DecimalMin(value = "0.00", message = "El fondo para mañana no puede ser negativo")
    private BigDecimal fondoSiguiente;

    /** Reemplaza las observaciones del corte; si se omite se conservan. */
    @Size(max = 1000, message = "Las observaciones no pueden exceder los 1000 caracteres")
    private String observaciones;

    @NotBlank(message = "Indique el motivo de la corrección")
    @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
    private String motivo;
}
