package com.skycel.backend.dto.producto;

import java.util.List;

/** Lo que hace (o haría) la migración de las refacciones del taller al tipo «Refacción». */
public record MigracionRefaccionesDto(
        boolean aplicado,
        List<String> talleres,
        /** Artículos del taller que pasan (o pasarían) a Refacción. */
        int articulos,
        /** Productos (filas por tienda) de esos artículos. */
        int productos,
        int productosConExistencia,
        /** Artículos del taller que ya eran Refacción. */
        int yaMigrados,
        boolean raizNueva,
        /** Categorías que se crean (o se crearían) bajo «Refacciones», con su camino. */
        List<String> categoriasNuevas,
        /** Categorías de antes (de Accesorios) que quedaron sin artículos y se retiraron. */
        List<String> categoriasRetiradas,
        /** Artículos del taller que también están en otras tiendas: no se tocan. */
        List<String> omitidos,
        /** Unos ejemplos del cambio: nombre, categoría de antes y de después. */
        List<String> ejemplos) {
}
