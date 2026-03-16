package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO Genérico para catálogos simples que solo tienen 'id' y 'nombre'
 * (Color, Seccion, Proveedor, Magnitud)
 */
@Data
public class CatalogoSimpleRequestDTO {
    
    @NotBlank(message = "El nombre no puede estar vacío")
    @Size(max = 30, message = "El nombre excede la longitud permitida")
    private String nombre;

}
