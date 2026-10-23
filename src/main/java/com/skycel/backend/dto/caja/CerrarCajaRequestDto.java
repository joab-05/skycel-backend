package com.skycel.backend.dto.caja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Cerrar la caja al terminar el día: genera el corte. */
@Data
public class CerrarCajaRequestDto {

    /** El efectivo que se contó en el cajón. */
    @NotNull(message = "El efectivo contado es obligatorio")
    @DecimalMin(value = "0.00", message = "El efectivo contado no puede ser negativo")
    private BigDecimal efectivoContado;

    /**
     * Lo que se deja en el cajón para mañana. El resto se retira. Si se omite se deja el fondo con el que se abrió (o lo
     * contado, si es menos).
     */
    @DecimalMin(value = "0.00", message = "El fondo para mañana no puede ser negativo")
    private BigDecimal fondoSiguiente;

    @Size(max = 1000, message = "Las observaciones no pueden exceder los 1000 caracteres")
    private String observaciones;
}
