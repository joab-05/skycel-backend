package com.skycel.backend.dto.servicios;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Lo vendido y lo ganado en servicios durante un mes (para ver cómo va mes con mes). */
@Data
@Builder
public class MesServiciosDto {
    /** AAAA-MM */
    private String     mes;
    private Integer    cortes;
    private Integer    operaciones;
    private BigDecimal monto;
    private BigDecimal ganancia;
}
