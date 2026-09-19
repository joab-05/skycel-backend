package com.skycel.backend.domain.dto.request;

import lombok.Data;
import java.math.BigDecimal;

/**
 * PUT /api/productos/{codpro}
 * Solo actualiza campos editables — stock se maneja por separado.
 */
@Data
public class ProductoUpdateDTO {
    private BigDecimal precioCompra;   // nuevo precio de compra
    private BigDecimal precioVenta;    // nuevo precio de venta
    private Short      idColor;
    private Short      idProveedor;
    private Short      idSeccion;
    private Short      idMagnitud;
}