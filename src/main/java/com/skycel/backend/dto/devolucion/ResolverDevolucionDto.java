package com.skycel.backend.dto.devolucion;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Al aprobar es opcional; al rechazar, el motivo es obligatorio. */
@Data
public class ResolverDevolucionDto {

    @Size(max = 255, message = "El comentario no puede exceder los 255 caracteres")
    private String comentario;
}
