package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelarOrdenRequestDto {

    @NotBlank(message = "El motivo de la cancelación es obligatorio")
    @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
    private String motivo;

    /**
     * Si es true, los anticipos en efectivo se devuelven al cliente (sale ese dinero de la caja). Por defecto
     * false: el anticipo se retiene. Los anticipos con tarjeta o transferencia se reembolsan por fuera del sistema.
     */
    private Boolean devolverAnticipo;
}
