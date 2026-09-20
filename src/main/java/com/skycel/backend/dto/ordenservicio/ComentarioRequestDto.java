package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ComentarioRequestDto {

    @NotBlank(message = "El comentario es obligatorio")
    private String comentario;
}
