package com.skycel.backend.dto.finanzastaller;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Algo que se le descuenta a un técnico de su comisión del mes. */
@Data
public class DescuentoComisionRequestDto {

    @NotNull(message = "El año es obligatorio")
    private Integer anio;

    @NotNull(message = "El mes es obligatorio")
    private Integer mes;

    @NotNull(message = "Elige al técnico")
    private Integer idusuario;

    @NotBlank(message = "Escribe el concepto del descuento")
    @Size(max = 150, message = "El concepto no puede exceder los 150 caracteres")
    private String concepto;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal monto;
}
