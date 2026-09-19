package com.skycel.backend.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * PATCH /api/productos/imei/{imei}/precio
 * precioVenta = null (o el campo ausente) quita el override y ese IMEI vuelve a
 * cobrar el precio del modelo; un valor numérico fija el precio propio de esa unidad.
 */
@Data
public class ImeiPrecioUpdateDTO {
    private BigDecimal precioVenta;
}
