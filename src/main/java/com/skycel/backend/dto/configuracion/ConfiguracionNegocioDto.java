package com.skycel.backend.dto.configuracion;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Datos del negocio: lo que responde GET y lo que recibe PUT (los campos de solo lectura se ignoran al guardar). */
@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ConfiguracionNegocioDto {

    @NotBlank(message = "El nombre comercial es obligatorio")
    @Size(max = 120, message = "El nombre comercial no puede exceder los 120 caracteres")
    private String nombreComercial;

    @Size(max = 150, message = "La razón social no puede exceder los 150 caracteres")
    private String razonSocial;

    /** RFC: 12 caracteres (persona moral) o 13 (persona física). */
    @Pattern(regexp = "^$|^[A-Za-zÑ&]{3,4}[0-9]{6}[A-Za-z0-9]{3}$", message = "El RFC no tiene un formato válido")
    private String rfc;

    @Size(max = 120, message = "El correo no puede exceder los 120 caracteres")
    private String email;

    @Size(max = 120, message = "El sitio web no puede exceder los 120 caracteres")
    private String sitioWeb;

    @Size(max = 255, message = "El pie del ticket no puede exceder los 255 caracteres")
    private String ticketPie;

    @Size(max = 255, message = "La leyenda no puede exceder los 255 caracteres")
    private String ticketLeyenda;

    @NotNull(message = "Los días para devoluciones son obligatorios")
    @Min(value = 0, message = "Los días para devoluciones no pueden ser negativos")
    @Max(value = 365, message = "Los días para devoluciones no pueden pasar de 365")
    private Integer diasDevolucion;

    // Solo lectura
    private LocalDateTime fechaActualizacion;
    private String actualizadoPor;
}
