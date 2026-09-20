package com.skycel.backend.dto.traspaso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Qué se hizo con lo que faltó: RECIBIDO_TARDE, REINTEGRADO_ORIGEN o BAJA. */
@Data
public class ResolverFaltanteRequestDto {

    @NotBlank
    private String accion;

    /** Cuánto se resuelve. Si se omite, todo lo que sigue pendiente del renglón. */
    private BigDecimal cantidad;

    @Size(max = 255)
    private String nota;
}
