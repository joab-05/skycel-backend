package com.skycel.backend.domain.enums;

/** Guardado como TINYINT (ordinal): agregar valores nuevos siempre al final, nunca reordenar ni insertar a la mitad. */
public enum TipoProducto {
    CELULAR,
    ACCESORIO,
    SERVICIO,
    TABLET,
    /** Pieza para reparar equipos (pantallas, baterías...). Se maneja como un accesorio (con existencias), pero no se vende en el punto de venta: se usa en las órdenes de servicio. */
    REFACCION
}