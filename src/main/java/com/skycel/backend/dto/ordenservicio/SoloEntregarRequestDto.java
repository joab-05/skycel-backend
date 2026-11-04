package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Entregar al cliente un equipo que no se pudo reparar: sin cobro y sin generar una venta. */
@Data
public class SoloEntregarRequestDto {

    /** Si la orden tiene anticipos en efectivo, se devuelven al cliente (obligatorio cuando hay anticipo). */
    private Boolean devolverAnticipo;

    @Size(max = 255, message = "Las observaciones no pueden exceder los 255 caracteres")
    private String observaciones;
}
