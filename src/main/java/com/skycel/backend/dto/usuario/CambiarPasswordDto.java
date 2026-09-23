package com.skycel.backend.dto.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** DTO para PATCH /api/usuarios/mi-password — cualquier usuario cambia su propia contraseña. */
@Data
public class CambiarPasswordDto {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La contraseña nueva es obligatoria")
    @Size(min = 8, message = "La contraseña nueva debe tener al menos 8 caracteres")
    private String passwordNueva;
}
