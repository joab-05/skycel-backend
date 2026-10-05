package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DescripcionRequestDTO {
    /** Categoría de la que cuelga (la más profunda: subcategoría 1 o 2). Obligatoria al crear. */
    private Short idCategoria;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 120, message = "La descripción no puede exceder los 120 caracteres")
    private String nombre;
}
