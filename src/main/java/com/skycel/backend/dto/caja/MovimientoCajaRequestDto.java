package com.skycel.backend.dto.caja;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MovimientoCajaRequestDto {

    /** Motivo del catálogo (define si es entrada o salida). Ver GET /api/motivos-caja */
    @NotNull(message = "El motivo es obligatorio")
    private Integer idmotivo;

    @NotNull(message = "El monto es obligatorio")
    @Positive(message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    /** Descripción libre y detallada del movimiento (opcional). */
    private String observaciones;
}
