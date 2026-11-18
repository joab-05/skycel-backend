package com.skycel.backend.dto.caja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agregar o cambiar un movimiento de efectivo de un corte ya cerrado (solo un administrador). Todo cambio lleva el motivo de la
 * corrección y queda registrado en el corte.
 */
@Data
public class CorreccionMovimientoRequestDto {

    /** El motivo del movimiento (de los que se capturan a mano; no los que registra el sistema). Obligatorio al agregar. */
    private Integer idmotivo;

    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal monto;

    @Size(max = 500, message = "La nota no puede exceder los 500 caracteres")
    private String observaciones;

    /** Solo al agregar: cuándo ocurrió (dentro del turno). Por omisión, justo antes del cierre. */
    private LocalDateTime fecha;

    /** Por qué se corrige el corte. Obligatorio. */
    @Size(max = 255, message = "El motivo de la corrección no puede exceder los 255 caracteres")
    private String motivoCorreccion;
}
