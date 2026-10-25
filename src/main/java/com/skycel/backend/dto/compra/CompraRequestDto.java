package com.skycel.backend.dto.compra;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CompraRequestDto {

    private Short idProveedor;     // obligatorio
    private Integer codti;         // obligatorio: sucursal que recibe la mercancía

    private String folioProveedor; // opcional: folio o factura del proveedor
    private LocalDate fecha;       // opcional, hoy si no se indica

    /** Opcional: si no se indica, se calcula con los días de crédito del proveedor (0 = de contado, vence hoy). */
    private LocalDate fechaVencimiento;

    private String observaciones;

    /**
     * Lo que se debe al proveedor por esta compra. Basta con el monto: la mercancía se da de alta aparte, en Inventario, que ya
     * guarda el proveedor, la fecha y la hora de cada entrada. Si se mandan {@code lineas}, el total sale de ellas.
     */
    private BigDecimal montoTotal;

    /** Opcional (las compras de antes): el detalle de la mercancía, que además la ingresa al inventario. */
    private List<CompraLineaRequestDto> lineas;
}
