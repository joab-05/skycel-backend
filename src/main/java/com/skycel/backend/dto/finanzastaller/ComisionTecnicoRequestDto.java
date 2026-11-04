package com.skycel.backend.dto.finanzastaller;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** El porcentaje de la ganancia neta que gana un técnico. */
@Data
public class ComisionTecnicoRequestDto {

    @NotNull(message = "El porcentaje es obligatorio")
    @DecimalMin(value = "0", message = "El porcentaje no puede ser negativo")
    @DecimalMax(value = "100", message = "El porcentaje no puede pasar de 100")
    private BigDecimal porcentaje;
}
