package com.skycel.backend.domain.enums;

/** Se guarda por su nombre: los estados nuevos (por revisar, aprobada...) se agregan después sin tocar los existentes. */
public enum EstadoTarea {
    PENDIENTE,
    HECHA,
    /** Hecha, pero espera que el encargado o el administrador la apruebe o la regrese. */
    POR_REVISAR,
    APROBADA
}
