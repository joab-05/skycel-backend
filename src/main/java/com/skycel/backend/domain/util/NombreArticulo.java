package com.skycel.backend.domain.util;

import com.skycel.backend.domain.entity.Categoria;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Arma el nombre completo de un artículo a partir de su camino de categorías, su descripción y su color:
 * {@code Celular > Samsung > A16 4/128gb + Verde} → "Celular Samsung A16 4/128gb Verde".
 * Solo entran las categorías marcadas con incluirEnNombre (la principal normalmente no).
 */
public final class NombreArticulo {

    private NombreArticulo() {}

    public static String construir(Categoria hoja, String nombreProducto) {
        return construir(hoja, nombreProducto, null);
    }

    public static String construir(Categoria hoja, String nombreProducto, String color) {
        List<String> partes = hoja.ruta().stream()
                .filter(Categoria::incluyeEnNombre)
                .map(Categoria::getNombre)
                .collect(Collectors.toList());
        return Stream.concat(partes.stream(), Stream.of(nombreProducto == null ? "" : nombreProducto, color == null ? "" : color))
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ");
    }
}
