package com.skycel.backend.dto.servicios;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Corte diario de servicios de una sucursal. Si ya hay uno capturado de ese día (sin confirmar), se reemplaza. */
@Data
public class CorteServiciosRequestDto {

    /** Sucursal. Opcional: si se omite es la del usuario. */
    private Integer codti;

    /** Día que se corta. Opcional: hoy. No puede ser futuro. */
    private LocalDate fecha;

    @Size(max = 255, message = "Las observaciones no pueden exceder los 255 caracteres")
    private String observaciones;

    @NotEmpty(message = "Capture al menos un servicio")
    @Valid
    private List<LineaDto> lineas;

    @Data
    public static class LineaDto {
        @NotNull(message = "El servicio es obligatorio")
        private Integer idtipo;

        /** Cuántas operaciones se hicieron. Obligatorio si el servicio cobra comisión por operación. */
        @Min(value = 0, message = "Las operaciones no pueden ser negativas")
        private Integer operaciones;

        /** Importe de las operaciones (sin la comisión). */
        @NotNull(message = "El monto es obligatorio (0 si no hubo)")
        @PositiveOrZero(message = "El monto no puede ser negativo")
        private BigDecimal monto;

        @PositiveOrZero(message = "El saldo inicial no puede ser negativo")
        private BigDecimal saldoInicial;

        @PositiveOrZero(message = "El fondeo no puede ser negativo")
        private BigDecimal fondeo;

        @PositiveOrZero(message = "El saldo final no puede ser negativo")
        private BigDecimal saldoFinal;
    }
}
