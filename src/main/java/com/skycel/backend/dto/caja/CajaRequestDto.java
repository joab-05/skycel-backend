package com.skycel.backend.dto.caja;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CajaRequestDto {

    @NotBlank(message = "El nombre de la caja es obligatorio")
    private String nombreCaja;

    @NotNull(message = "La tienda es obligatoria")
    private Integer codti;

    private Boolean esCajaPrincipal = false;
}