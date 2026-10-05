package com.skycel.backend.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Reporte de la migración al árbol de categorías (lo que haría o lo que hizo). */
@Data
public class MigracionArbolResultadoDTO {

    /** false = solo plan (nada se guardó); true = los cambios quedaron guardados. */
    private boolean aplicado;
    private int migrados;
    private int yaMigrados;
    private int tabletsComoEquipo;
    private List<String> categoriasCreadas = new ArrayList<>();
    private List<String> categoriasDesactivadas = new ArrayList<>();
    private List<String> advertencias = new ArrayList<>();
    private List<Linea> lineas = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Linea {
        private Integer idprodmaster;
        private String nombreAnterior;
        private String nombreNuevo;
        private String ruta;
        /** ACTUALIZADO (en su lugar) o DIVIDIDO_POR_COLOR (un artículo nuevo por color). */
        private String accion;
        private int productos;
    }
}
