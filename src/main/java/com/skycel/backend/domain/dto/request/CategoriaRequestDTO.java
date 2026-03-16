package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoriaRequestDTO {
    
    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 20, message = "El nombre no puede exceder los 20 caracteres")
    private String nombreCat;

    private Short idCategoriaSuperior;

    // We do NOT expose idcat (Auto-generated), activo (Always true on create), or audit fields
}
