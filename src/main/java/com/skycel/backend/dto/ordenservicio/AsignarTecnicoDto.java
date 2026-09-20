package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AsignarTecnicoDto {

    /** Usuario con rol TECNICO. */
    @NotNull(message = "El técnico es obligatorio")
    private Integer idTecnico;
}
