package com.skycel.backend.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * PATCH /api/productos/imei/{imei}
 * Corrige la condición o el costo de una unidad disponible. Los campos que no se envían no cambian.
 */
@Data
public class ImeiUpdateDTO {
    /** NUEVO, USADO o REACONDICIONADO. */
    private String condicion;
    /** Costo de compra de esta unidad. */
    private BigDecimal costoUnitario;
}
