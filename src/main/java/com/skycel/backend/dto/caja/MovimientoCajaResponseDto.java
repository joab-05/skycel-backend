package com.skycel.backend.dto.caja;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MovimientoCajaResponseDto {
    private Integer       idmovimiento;
    private Integer       idCaja;
    private String        nombreCaja;
    private Integer       idmotivo;
    private String        nombreMotivo;
    /** 1 = Entrada, 2 = Salida */
    private Byte          tipo;
    private String        tipoDisplay;
    private BigDecimal    monto;
    private Integer       idusuarioEncargado;
    private String        nombreEncargado;
    private Integer       idusuarioAdmin;
    /** Movimiento relacionado (ej. la entrada original que revierte una cancelación) */
    private Integer       idmovRef;
    private LocalDateTime fechaMov;
    private String        observaciones;
}
