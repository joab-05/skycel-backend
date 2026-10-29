package com.skycel.backend.dto.producto;

import java.math.BigDecimal;

/**
 * Cuánto hay de un artículo en una sucursal (para saber a quién pedírselo en un traspaso). Un accesorio se identifica por su
 * código en esa sucursal; un equipo, por el artículo ({@code idprodmaster}) y sus unidades disponibles por IMEI o serie.
 */
public record ExistenciaTiendaDto(
        Integer codti,
        String tienda,
        boolean esAlmacen,
        boolean esEquipo,
        String codpro,
        Integer idprodmaster,
        String nombre,
        BigDecimal existencia,
        BigDecimal precio) {
}
