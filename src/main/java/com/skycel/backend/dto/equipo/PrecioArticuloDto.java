package com.skycel.backend.dto.equipo;

import lombok.Data;

import java.math.BigDecimal;

/** Pone el mismo precio de venta a todas las unidades disponibles de un artículo. */
@Data
public class PrecioArticuloDto {
    private BigDecimal precioVenta;
    /** Si se indica, también se actualiza el costo de todas. */
    private BigDecimal costo;
}
