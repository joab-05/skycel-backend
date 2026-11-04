package com.skycel.backend.dto.finanzastaller;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un gasto del negocio. */
@Data
public class GastoRequestDto {

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotBlank(message = "Escribe en qué se gastó")
    @Size(max = 150, message = "El concepto no puede exceder los 150 caracteres")
    private String concepto;

    @NotBlank(message = "Elige la categoría")
    private String categoria;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal monto;

    /** Sucursal a la que corresponde; vacío = general. */
    private Integer codti;

    /** Se cuenta solo cada mes (la renta). */
    private Boolean recurrente;

    /** Último mes en que cuenta un gasto recurrente; vacío = sin fin. */
    private LocalDate hastaFecha;
}
