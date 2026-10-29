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
    /** Lo que queda de ganancia en el periodo. */
    private BigDecimal totalGanancia;
    private Integer    totalOperaciones;
    private List<TipoDto> porServicio;
    private List<SucursalDto> porSucursal;

    @Data
    @Builder
    public static class TipoDto {
        private String     nombre;
        private Integer    operaciones;
        private BigDecimal monto;
        private BigDecimal comision;
        private BigDecimal ganancia;
        /** true = solo se cuentan las operaciones (clientes), sin ganancia calculada. */
        private Boolean    soloConteo;
    }

    @Data
    @Builder
    public static class SucursalDto {
        private Integer    codti;
        private String     nombre;
        private Integer    operaciones;
        private BigDecimal monto;
        private BigDecimal ganancia;
    }
}
