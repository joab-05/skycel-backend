package com.skycel.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cuando un administrador corrige el código de un producto, los documentos que lo guardan por código (ventas, traspasos,
 * órdenes de servicio, devoluciones, conteos de inventario) pasan a llevar el código nuevo: así una venta vieja sigue
 * encontrando su producto (por ejemplo, para abrir una garantía) y un traspaso en camino no se queda con un código que ya no existe.
 */
@Component
@RequiredArgsConstructor
public class RenombradorCodigoProducto {

    /** Tablas y columna con el código; nombres fijos, nunca datos del usuario. */
    private static final String[][] TABLAS = {
            {"venta_detalle", "codpro"},
            {"traspaso_detalle", "codpro"},
            {"orden_servicio_detalle", "codpro"},
            {"devolucion_detalle", "codpro"},
            {"devolucion_cambio_linea", "codpro"},
            {"inventario_auditoria_detalle", "codpro"},
    };

    private final JdbcTemplate jdbc;

    public void renombrar(String viejo, String nuevo) {
        for (String[] t : TABLAS) {
            jdbc.update("UPDATE " + t[0] + " SET " + t[1] + " = ? WHERE " + t[1] + " = ?", nuevo, viejo);
        }
    }
}
