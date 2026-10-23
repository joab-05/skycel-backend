package com.skycel.backend.ayuda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El manual de usuario (static/app/ayuda/manual.md) lo leen la web y JSystem, y F1 abre el tema de cada pantalla por su
 * título. Si alguien renombra o borra un tema, F1 dejaría de llevar a donde debe: esta prueba lo avisa.
 */
class ManualAyudaTest {

    /** Los temas a los que apuntan F1 y el botón de ayuda, en la web (temaDeRuta) y en JSystem (Manual.temaDeSeccion). */
    private static final List<String> TEMAS_QUE_ABRE_F1 = List.of(
            "Primeros pasos", "Punto de Venta", "Caja", "Devoluciones", "Inventario", "Inventario físico", "Traspasos",
            "Clientes", "Cuentas por Cobrar", "Garantías", "Órdenes de Servicio", "Corte de Servicios",
            "Compras y Proveedores", "Descuentos", "Reportes", "Empleados, nómina y comisiones", "Trabajar sin conexión");

    private static Map<String, Integer> temas() throws IOException {
        try (InputStream in = ManualAyudaTest.class.getResourceAsStream("/static/app/ayuda/manual.md")) {
            assertThat(in).as("falta static/app/ayuda/manual.md").isNotNull();
            String md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Integer> temas = new LinkedHashMap<>();
            String actual = null;
            for (String linea : md.replace("\r\n", "\n").split("\n")) {
                if (linea.startsWith("## ")) {
                    actual = linea.substring(3).trim();
                    temas.put(actual, 0);
                } else if (actual != null && !linea.isBlank()) {
                    temas.merge(actual, 1, Integer::sum);
                }
            }
            return temas;
        }
    }

    @Test
    @DisplayName("cada tema al que apunta F1 existe en el manual")
    void temasDeF1Existen() throws IOException {
        assertThat(temas().keySet()).containsAll(TEMAS_QUE_ABRE_F1);
    }

    @Test
    @DisplayName("ningún tema del manual está vacío ni repetido")
    void temasConContenido() throws IOException {
        Map<String, Integer> temas = temas();
        List<String> vacios = new ArrayList<>();
        temas.forEach((t, lineas) -> { if (lineas < 1) vacios.add(t); });
        assertThat(vacios).as("temas sin contenido").isEmpty();
        long titulos = new String(ManualAyudaTest.class.getResourceAsStream("/static/app/ayuda/manual.md").readAllBytes(), StandardCharsets.UTF_8)
                .lines().filter(l -> l.startsWith("## ")).count();
        assertThat(titulos).as("hay títulos de tema repetidos").isEqualTo(temas.size());
    }
}
