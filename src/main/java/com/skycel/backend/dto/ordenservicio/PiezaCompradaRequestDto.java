package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Una pieza que el técnico compró para esta orden y que no estaba en el inventario del taller. */
@Data
public class PiezaCompradaRequestDto {

    /** Qué es (ej. «Pantalla Samsung A16»). */
    @NotBlank(message = "Describe la pieza")
    @Size(max = 80, message = "La descripción no puede exceder los 80 caracteres")
    private String descripcion;

    /** Por defecto 1. */
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Short cantidad;

    /** Lo que costó cada pieza. */
    @NotNull(message = "Indica cuánto costó la pieza")
    @PositiveOrZero(message = "El costo no puede ser negativo")
    private BigDecimal costo;

    /** Lo que se le cobra al cliente por cada pieza. */
    @NotNull(message = "Indica el precio al cliente")
    @DecimalMin(value = "0.01", message = "El precio al cliente debe ser mayor a cero")
    private BigDecimal precio;
}
