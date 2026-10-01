package com.skycel.backend.dto.comision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ConfigComisionDto {

    @NotNull @PositiveOrZero
    private BigDecimal tasaEncargadoMensual;

    @NotNull @PositiveOrZero
    private BigDecimal payjoyUmbral;

    @NotNull @PositiveOrZero
    private BigDecimal payjoyTasaBaja;

    @NotNull @PositiveOrZero
    private BigDecimal payjoyTasaAlta;

    private LocalDateTime fechaActualizacion;
    private String actualizadoPor;
}
