package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductoMasterRequestDTO {

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
    private String nombre;

    @Size(max = 255, message = "La descripción no puede exceder los 255 caracteres")
    private String descripcion;

    @NotNull(message = "La categoría es obligatoria")
    private Short idCategoria;

    private String urlImagen;

    @NotBlank(message = "El tipo de producto es obligatorio")
    private String tipo; // CELULAR, ACCESORIO, SERVICIO
}
