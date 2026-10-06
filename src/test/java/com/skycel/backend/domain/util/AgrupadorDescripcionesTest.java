package com.skycel.backend.domain.util;

import com.skycel.backend.domain.util.AgrupadorDescripciones.Asignacion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgrupadorDescripcionesTest {

    @Test
    void la_primera_palabra_sin_conectores_pasa_a_subcategoria_y_el_resto_queda_como_descripcion() {
        Map<String, Asignacion> r = AgrupadorDescripciones.agrupar(
                List.of("de Pared Iphone Tipo C 20w", "de Pared Samsung 25w", "de Auto Dual", "de Auto 1Hora"), 2);
        assertThat(r.get("de Pared Iphone Tipo C 20w")).isEqualTo(new Asignacion("Pared", "Iphone Tipo C 20w"));
        assertThat(r.get("de Pared Samsung 25w")).isEqualTo(new Asignacion("Pared", "Samsung 25w"));
        assertThat(r.get("de Auto Dual")).isEqualTo(new Asignacion("Auto", "Dual"));
    }

    @Test
    void un_grupo_de_un_solo_articulo_no_crea_subcategoria() {
        Map<String, Asignacion> r = AgrupadorDescripciones.agrupar(List.of("Pared Iphone", "Pared Samsung", "Otro Unico"), 2);
        assertThat(r).containsKeys("Pared Iphone", "Pared Samsung");
        assertThat(r).doesNotContainKey("Otro Unico");
    }

    @Test
    void si_casi_todos_comparten_la_segunda_palabra_va_con_el_nombre_del_grupo() {
        Map<String, Asignacion> r = AgrupadorDescripciones.agrupar(
                List.of("Uso Rudo Motorola G55", "Uso Rudo Samsung A15", "Uso Rudo Iphone 13", "Uso Rudo Xiaomi 13C"), 2);
        assertThat(r.get("Uso Rudo Motorola G55")).isEqualTo(new Asignacion("Uso Rudo", "Motorola G55"));
        assertThat(r.get("Uso Rudo Xiaomi 13C")).isEqualTo(new Asignacion("Uso Rudo", "Xiaomi 13C"));
    }

    @Test
    void si_la_segunda_palabra_varia_el_grupo_es_solo_la_primera() {
        Map<String, Asignacion> r = AgrupadorDescripciones.agrupar(
                List.of("Antigolpe Samsung A15", "Antigolpe Motorola G55", "Antigolpe Iphone 13"), 2);
        assertThat(r.get("Antigolpe Samsung A15")).isEqualTo(new Asignacion("Antigolpe", "Samsung A15"));
    }

    @Test
    void las_que_no_comparten_la_extension_se_agrupan_aparte_solo_por_la_primera_palabra() {
        Map<String, Asignacion> r = AgrupadorDescripciones.agrupar(java.util.stream.Stream.concat(
                java.util.stream.IntStream.range(0, 20).mapToObj(i -> "Uso Rudo Modelo" + i),
                java.util.stream.Stream.of("Uso Normal A1", "Uso Normal B2")).toList(), 2);
        assertThat(r.get("Uso Rudo Modelo3")).isEqualTo(new Asignacion("Uso Rudo", "Modelo3"));
        assertThat(r.get("Uso Normal A1")).isEqualTo(new Asignacion("Uso", "Normal A1"));
    }

    @Test
    void si_la_descripcion_es_solo_la_primera_parte_no_queda_resto() {
        Map<String, Asignacion> r = AgrupadorDescripciones.agrupar(List.of("Pared", "de Pared Iphone"), 2);
        assertThat(r.get("Pared")).isEqualTo(new Asignacion("Pared", null));
    }

    @Test
    void ignora_mayusculas_al_agrupar_y_conserva_la_ortografia_del_primero() {
        Map<String, Asignacion> r = AgrupadorDescripciones.agrupar(List.of("Pared Iphone", "PARED Samsung"), 2);
        assertThat(r.get("PARED Samsung").subcategoria()).isEqualTo("Pared");
        assertThat(r.get("PARED Samsung").resto()).isEqualTo("Samsung");
    }
}
