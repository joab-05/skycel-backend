package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** El técnico reporta que la reparación no quedó: por qué (un motivo de la lista) y lo que pasó. */
@Data
public class NoQuedoRequestDto {

    @NotBlank(message = "Elige el motivo por el que no quedó")
    private String motivo;

    @Size(max = 255, message = "El detalle no puede exceder los 255 caracteres")
    private String detalle;
}
