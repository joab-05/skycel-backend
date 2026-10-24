package com.skycel.backend.dto.equipo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Un artículo con cuántas unidades hay en una sucursal: es lo que se ve como «el producto» en inventario y en el punto de venta. */
@Data
@Builder
public class EquipoArticuloDto {
    private Integer idprodmaster;
    private String nombreArticulo;
    /** CELULAR o TABLET. */
    private String tipo;
    private Short idCategoria;
    private String categoriaPrincipal;
    private String subcategoria1;
    private String subcategoria2;
    private String colorArticulo;
    private Integer codti;
    private String nombreTienda;
    private long disponibles;
    private long enGarantia;
    private BigDecimal precioMinimo;
    private BigDecimal precioMaximo;
    private Integer stockMinimo;
    private Boolean bajoStock;
    /** Las unidades disponibles (solo cuando se piden). */
    private List<EquipoResponseDto> unidades;
}
