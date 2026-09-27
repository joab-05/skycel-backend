package com.skycel.backend.dto.compra;

import lombok.Data;

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

    private List<CompraLineaRequestDto> lineas; // al menos una
}
