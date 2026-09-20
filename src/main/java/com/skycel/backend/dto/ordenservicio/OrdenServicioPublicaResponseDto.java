package com.skycel.backend.dto.ordenservicio;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que ve el cliente al consultar su orden con el folio, sin iniciar sesión. Solo seguimiento: nunca importes,
 * técnico, diagnóstico, datos personales ni las anotaciones internas de la bitácora.
 */
@Data
@Builder
public class OrdenServicioPublicaResponseDto {
    private String  folio;
    /** Marca y modelo del equipo. */
    private String  equipo;
    /** IMEI o serie enmascarado: solo los últimos 4 caracteres. */
    private String  imei;
    private String  sucursal;
    private String  estado;
    /** Explicación del estado en lenguaje para el cliente. */
    private String  mensaje;
    private LocalDateTime fechaIngreso;
    private LocalDate     fechaPromesa;
    private LocalDateTime fechaLista;
    private LocalDateTime fechaEntrega;
    /** Garantía del trabajo, una vez entregado. */
    private Integer   diasGarantia;
    private LocalDate garantiaHasta;
    /** Solo los cambios de estado, sin anotaciones internas. */
    private List<PasoDto> avance;

    @Data
    @Builder
    public static class PasoDto {
        private String        estado;
        private LocalDateTime fecha;
    }
}
