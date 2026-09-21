package com.skycel.backend.dto.servicios;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** El administrador confirma el corte con lo que reporta cada proveedor. */
@Data
public class ConfirmarCorteRequestDto {

    /** Un renglón por cada servicio del corte, con lo que reporta el proveedor. */
    @NotEmpty(message = "Indique lo que reporta el proveedor de cada servicio")
    @Valid
    private List<LineaDto> lineas;

    /** Obligatorio si hay diferencias: a qué se deben. */
    @Size(max = 255, message = "El comentario no puede exceder los 255 caracteres")
    private String comentario;

    @Data
    public static class LineaDto {
        @NotNull(message = "El renglón del corte es obligatorio")
        private Integer idlinea;
        @NotNull(message = "Lo que reporta el proveedor es obligatorio")
        @PositiveOrZero(message = "El monto no puede ser negativo")
        private BigDecimal montoProveedor;
    }
}
