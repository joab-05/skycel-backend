package com.skycel.backend.dto.descuento;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DescuentoReglaRequestDto {

    private String nombre;               // obligatorio

    private String tipoProducto;         // "CELULAR"|"ACCESORIO"|"SERVICIO"|"TABLET"|null = todos
    private Integer idProductoMaster;    // null = todos los artículos (del tipo, si se indicó)
    private Integer codti;               // null = todas las sucursales

    private BigDecimal montoMinimo;      // null/0 = sin mínimo

    private BigDecimal descuentoPorcentaje; // exactamente uno de los dos
    private BigDecimal descuentoFijo;

    private String aplicacion;           // "SIEMPRE" | "DIAS_SEMANA" | "RANGO_FECHA"
    private java.util.List<String> diasSemana; // ["LUN","MIE",...], solo si aplicacion = DIAS_SEMANA
    private LocalDate fechaInicio;       // solo si aplicacion = RANGO_FECHA
    private LocalDate fechaFin;

    private Boolean activo;
}
