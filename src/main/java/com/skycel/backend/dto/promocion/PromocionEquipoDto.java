package com.skycel.backend.dto.promocion;

import lombok.Data;

import java.math.BigDecimal;

/** La configuración de la promoción al vender un equipo, tal como se lee y se guarda. */
@Data
public class PromocionEquipoDto {
    private Boolean activo;
    private Short idCategoriaChip;
    private Short idCategoriaMica;
    private Short idCategoriaProtector;
    /** Nombres de las categorías elegidas, solo al leer (para mostrarlos). */
    private String categoriaChip;
    private String categoriaMica;
    private String categoriaProtector;
    private BigDecimal descuentoMica;
    private BigDecimal descuentoProtector;
    private BigDecimal protectorPrecioMenorA;
    private Integer chipsPorEquipo;
    private Integer micasPorEquipo;
    private Integer protectoresPorEquipo;
}
