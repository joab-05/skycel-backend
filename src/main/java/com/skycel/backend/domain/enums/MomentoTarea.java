package com.skycel.backend.domain.enums;

/** En qué parte del día toca una actividad. El orden de declaración es el orden en que se muestran. */
public enum MomentoTarea {
    APERTURA("Apertura"),
    DURANTE("Durante el día"),
    CIERRE("Cierre");

    private final String etiqueta;

    MomentoTarea(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
