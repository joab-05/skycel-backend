package com.skycel.backend.domain.util;

import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.enums.TipoProducto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NombreArticuloTest {

    private Categoria cat(String nombre, Categoria padre, boolean incluir) {
        return Categoria.builder().nombre(nombre).tipo(TipoProducto.ACCESORIO)
                .categoriaSuperior(padre).incluirEnNombre(incluir).activo(true).build();
    }

    @Test
    void equipo_incluye_subcategorias_y_variante_pero_no_la_principal() {
        Categoria equipos = cat("Equipos", null, false);
        Categoria celular = cat("Celular", equipos, true);
        Categoria samsung = cat("Samsung", celular, true);
        assertThat(NombreArticulo.construir(samsung, "A16 4/128gb Verde")).isEqualTo("Celular Samsung A16 4/128gb Verde");
    }

    @Test
    void servicio_omite_la_subcategoria_marcada_como_no_incluida() {
        Categoria servicios = cat("Servicios", null, false);
        Categoria reparacion = cat("Reparación", servicios, false);
        Categoria pantalla = cat("Cambio de Pantalla", reparacion, true);
        assertThat(NombreArticulo.construir(pantalla, "iPhone 13")).isEqualTo("Cambio de Pantalla iPhone 13");
    }

    @Test
    void sin_variante_el_nombre_es_solo_el_camino() {
        Categoria accesorios = cat("Accesorios", null, false);
        Categoria cable = cat("Cable", accesorios, true);
        assertThat(NombreArticulo.construir(cable, null)).isEqualTo("Cable");
        assertThat(NombreArticulo.construir(cable, "   ")).isEqualTo("Cable");
    }

    @Test
    void sin_marca_la_subcategoria_2_es_opcional() {
        Categoria accesorios = cat("Accesorios", null, false);
        Categoria funda = cat("Funda", accesorios, true);
        assertThat(NombreArticulo.construir(funda, "iPhone 13 Transparente")).isEqualTo("Funda iPhone 13 Transparente");
    }

    @Test
    void el_color_va_al_final_despues_de_la_descripcion() {
        Categoria equipos = cat("Equipos", null, false);
        Categoria celular = cat("Celular", equipos, true);
        Categoria samsung = cat("Samsung", celular, true);
        assertThat(NombreArticulo.construir(samsung, "A16 4/128gb", "Verde")).isEqualTo("Celular Samsung A16 4/128gb Verde");
        assertThat(NombreArticulo.construir(samsung, null, "Verde")).isEqualTo("Celular Samsung Verde");
        assertThat(NombreArticulo.construir(samsung, "A16", null)).isEqualTo("Celular Samsung A16");
    }

    @Test
    void ruta_nivel_y_subcategoria1() {
        Categoria equipos = cat("Equipos", null, false);
        Categoria celular = cat("Celular", equipos, true);
        Categoria samsung = cat("Samsung", celular, true);
        assertThat(samsung.nivel()).isEqualTo(3);
        assertThat(samsung.subcategoria1()).isSameAs(celular);
        assertThat(celular.subcategoria1()).isSameAs(celular);
        assertThat(equipos.subcategoria1()).isNull();
        assertThat(samsung.raiz()).isSameAs(equipos);
    }
}
