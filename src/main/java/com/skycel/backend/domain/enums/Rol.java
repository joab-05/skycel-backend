package com.skycel.backend.domain.enums;

/**
 * El rol se guarda por su posición (ordinal): los valores nuevos se agregan SIEMPRE al final,
 * para no cambiar el rol de los usuarios existentes.
 */
public enum Rol {
    ROOT,              // 0
    ADMIN,             // 1
    ENCARGADO_TIENDA,  // 2
    VENDEDOR,          // 3
    PROVEEDOR,         // 4
    CLIENTE_MAYOREO,   // 5
    TECNICO            // 6 — repara equipos; se asigna a las órdenes de servicio
}
