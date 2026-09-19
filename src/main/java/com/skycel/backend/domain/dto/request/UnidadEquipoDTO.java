package com.skycel.backend.domain.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Una unidad de un equipo (celular o tablet) con sus datos propios. Alternativa a la lista simple
 * de IMEIs cuando las unidades no son idénticas (por ejemplo, un usado comprado a un cliente).
 */
@Data
public class UnidadEquipoDTO {

    /** IMEI o número de serie (5 a 20 caracteres alfanuméricos). */
    private String imei;

    /** NUEVO (por defecto), USADO o REACONDICIONADO. */
    private String condicion;

    /** Costo de compra de esta unidad. Si se omite se usa el costo del modelo. */
    private BigDecimal costoUnitario;

    /** Precio de venta propio de esta unidad. Si se omite se usa el precio del modelo. */
    private BigDecimal precioVenta;
}
