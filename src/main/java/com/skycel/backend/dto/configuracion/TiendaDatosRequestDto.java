package com.skycel.backend.dto.configuracion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Datos de una sucursal que salen en su ticket. */
@Data
public class TiendaDatosRequestDto {

    @NotBlank(message = "El nombre de la sucursal es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
    private String nombre;

    @Size(max = 255, message = "El domicilio no puede exceder los 255 caracteres")
    private String ubicacion;

    @Size(max = 20, message = "El teléfono no puede exceder los 20 caracteres")
    private String telefono;
}
