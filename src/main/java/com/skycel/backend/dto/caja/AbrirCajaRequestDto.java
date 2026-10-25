package com.skycel.backend.dto.caja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Abrir la caja al iniciar el día. */
@Data
public class AbrirCajaRequestDto {

    /** Efectivo con el que se abre (lo que hay en el cajón). */
    @NotNull(message = "El fondo inicial es obligatorio")
    @DecimalMin(value = "0.00", message = "El fondo inicial no puede ser negativo")
    private BigDecimal fondoInicial;

    /** Solo si la apertura se hizo sin conexión: cuándo ocurrió de verdad y su clave (reenviarla no la duplica). */
    private java.time.LocalDateTime fecha;

    @jakarta.validation.constraints.Size(max = 64, message = "La clave no puede exceder los 64 caracteres")
    private String claveOffline;
}
