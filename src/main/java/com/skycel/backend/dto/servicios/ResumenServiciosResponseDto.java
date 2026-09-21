package com.skycel.backend.dto.servicios;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Totales por servicio de los cortes de un periodo. */
@Data
@Builder
public class ResumenServiciosResponseDto {
    private LocalDate desde;
    private LocalDate hasta;
    private Integer   cortes;
    private Integer   cortesSinConfirmar;
    private Integer   cortesConDiferencias;
    private BigDecimal totalMonto;
    private BigDecimal totalComision;
    private List<TipoDto> porServicio;

    @Data
    @Builder
    public static class TipoDto {
        private String     nombre;
        private Integer    operaciones;
        private BigDecimal monto;
        private BigDecimal comision;
    }
}
