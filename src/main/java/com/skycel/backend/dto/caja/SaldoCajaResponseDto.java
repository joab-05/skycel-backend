package com.skycel.backend.dto.caja;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SaldoCajaResponseDto {
    private Integer    idCaja;
    private String     nombreCaja;
    private BigDecimal totalEntradas;
    private BigDecimal totalSalidas;
    private BigDecimal saldo;
}
