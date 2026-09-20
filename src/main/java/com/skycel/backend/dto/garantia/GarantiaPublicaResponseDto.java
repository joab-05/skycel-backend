package com.skycel.backend.dto.garantia;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que ve el cliente al consultar su garantía con el folio, sin iniciar sesión. Solo información de seguimiento:
 * nunca datos personales, usuarios ni los comentarios internos.
 */
@Data
@Builder
public class GarantiaPublicaResponseDto {
    private String  folio;
    private String  producto;
    /** IMEI enmascarado: solo los últimos 4 dígitos. */
    private String  imei;
    private String  sucursal;
    private LocalDateTime fechaIngreso;
    private LocalDate     fechaLimiteSolucion;
    private String  estado;
    /** Explicación del estado en lenguaje para el cliente. */
    private String  mensaje;
    private List<PasoDto> avance;

    @Data
    @Builder
    public static class PasoDto {
        private String        estado;
        private LocalDateTime fecha;
    }
}
