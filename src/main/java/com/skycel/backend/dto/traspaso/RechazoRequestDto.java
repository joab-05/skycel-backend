package com.skycel.backend.dto.traspaso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RechazoRequestDto {

    @NotBlank(message = "El motivo del rechazo es obligatorio")
    @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
    private String motivo;
}
