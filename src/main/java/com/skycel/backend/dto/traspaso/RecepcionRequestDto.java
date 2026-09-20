package com.skycel.backend.dto.traspaso;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cómo llegó un envío. Sin cuerpo (o sin faltantes) se recibe completo. Cada faltante indica lo que NO llegó: en un
 * accesorio, la cantidad; en un equipo, los IMEI que faltan.
 */
@Data
public class RecepcionRequestDto {

    @Valid
    private List<FaltanteDto> faltantes;

    /** Obligatorio cuando hay faltantes: qué pasó. */
    @Size(max = 255)
    private String comentario;

    @Data
    public static class FaltanteDto {
        private String codpro;
        /** Accesorios: cuánto no llegó. */
        private BigDecimal cantidad;
        /** Equipos: los IMEI que no llegaron. */
        private List<String> imeis;
    }
}
