package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SeccionRequestDTO {
    @NotNull(message = "El código de tienda (codti) es obligatorio")
    private Integer codti;

    @NotBlank(message = "El nombre de la sección no puede estar vacío")
    @Size(max = 50, message = "El nombre excede la longitud permitida")
    private String nombre;
}
