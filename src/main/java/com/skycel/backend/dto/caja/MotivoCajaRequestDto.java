package com.skycel.backend.dto.caja;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MotivoCajaRequestDto {

    @NotBlank(message = "El nombre del motivo es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
    private String nombre;

    /** 1 = Entrada (suma a la caja), 2 = Salida (resta) */
    @NotNull(message = "El tipo de movimiento es obligatorio")
    @Min(value = 1, message = "tipoMov debe ser 1 (Entrada) o 2 (Salida)")
    @Max(value = 2, message = "tipoMov debe ser 1 (Entrada) o 2 (Salida)")
    private Byte tipoMov;

    /** 1 = Operativo, 2 = Nómina, 3 = Fiscal, 4 = Traslado, 5 = Personal (exige ROOT/ADMIN) */
    @NotNull(message = "La clasificación es obligatoria")
    @Min(value = 1, message = "catSat debe estar entre 1 y 5")
    @Max(value = 5, message = "catSat debe estar entre 1 y 5")
    private Byte catSat;
}
