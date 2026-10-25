package com.skycel.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Los equipos (celulares y tablets) dejaron de ser un producto con código por modelo: cada unidad se identifica por su IMEI
 * o serie y apunta directo a su artículo y a su sucursal. Este arranque lleva los datos existentes al modelo nuevo:
 * <ol>
 *   <li>quita el NOT NULL de las columnas que antes exigían un producto (ddl-auto: update no lo hace);</li>
 *   <li>a cada unidad le copia de su producto el artículo, la sucursal, el proveedor, la sección, el precio, el costo y si era rezagada;</li>
 *   <li>da de baja (inactivos) los productos por modelo de los equipos, que ya no se usan;</li>
 *   <li>pasa los renglones de traspasos y las garantías de equipos a apuntar al artículo, sin código.</li>
 * </ol>
 * Es seguro correrlo las veces que sea: cada paso solo toca lo que todavía no está migrado.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class EquiposEsquemaInitializer implements CommandLineRunner {

    private static final String[][] COLUMNAS_OPCIONALES = {
            {"producto_imei", "idproducto"},
            {"movimiento_inventario", "idproducto"},
            {"traspaso_detalle", "codpro"},
            {"compra_detalle", "idproducto"},
            {"inventario_auditoria_detalle", "idseccion"},
    };

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        try {
            for (String[] c : COLUMNAS_OPCIONALES) aceptarNulos(c[0], c[1]);
            if (!existeColumna("producto_imei", "idprodmaster") || !existeColumna("producto_imei", "codti")) return;

            // Las unidades heredan del producto al que pertenecían
            int unidades = jdbc.update("""
                    UPDATE producto_imei pi JOIN producto p ON pi.idproducto = p.idproducto
                    SET pi.idprodmaster = p.idprodmaster,
                        pi.codti = p.codti,
                        pi.idproveedor = COALESCE(pi.idproveedor, p.idproveedor),
                        pi.idseccion = COALESCE(pi.idseccion, p.idseccion),
                        pi.precio_venta_override = COALESCE(pi.precio_venta_override, p.preciopub),
                        pi.costo_unitario = COALESCE(pi.costo_unitario, p.preciopro),
                        pi.rezagado = COALESCE(pi.rezagado, p.rezagado, 0)
                    WHERE pi.idprodmaster IS NULL
                    """);
            jdbc.update("UPDATE producto_imei SET rezagado = 0 WHERE rezagado IS NULL");
            int seminuevos = jdbc.update("UPDATE producto_imei SET condicion = 'SEMINUEVO' WHERE condicion = 'USADO'");
            if (seminuevos > 0) log.info("Equipos: {} unidades «usado» pasaron a «seminuevo»", seminuevos);
            jdbc.update("""
                    UPDATE producto_imei
                    SET tipo_identificador = CASE WHEN imei REGEXP '^[0-9]{15}$' THEN 'IMEI' ELSE 'SERIE' END
                    WHERE tipo_identificador IS NULL
                    """);
            if (unidades > 0) log.info("Equipos: {} unidades pasaron al modelo por IMEI", unidades);

            // El producto por modelo de un equipo ya no se usa
            int productos = jdbc.update("""
                    UPDATE producto p JOIN producto_master m ON p.idprodmaster = m.idprodmaster
                    SET p.activo = 0
                    WHERE m.tipo IN (0, 3) AND p.activo = 1
                    """);
            if (productos > 0) log.info("Equipos: {} productos por modelo dados de baja (ahora se manejan por IMEI)", productos);

            // Traspasos y garantías de equipos apuntan al artículo, no a un código
            jdbc.update("""
                    UPDATE traspaso_detalle d JOIN producto_imei pi ON pi.imei COLLATE utf8mb4_unicode_ci = d.imei COLLATE utf8mb4_unicode_ci
                    SET d.idprodmaster = pi.idprodmaster, d.codpro = NULL
                    WHERE d.imei IS NOT NULL AND d.idprodmaster IS NULL
                    """);
            jdbc.update("""
                    UPDATE garantia g JOIN producto p ON g.idproducto = p.idproducto
                    SET g.idprodmaster = p.idprodmaster
                    WHERE g.idprodmaster IS NULL
                    """);
        } catch (Exception e) {
            // Otra base de datos (pruebas) o las tablas aún no existen: no impide arrancar
            log.warn("No se pudo migrar el esquema de equipos: {}", e.getMessage());
        }
    }

    private boolean existeColumna(String tabla, String columna) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tabla, columna);
        return n != null && n > 0;
    }

    private void aceptarNulos(String tabla, String columna) {
        List<Map<String, Object>> obligatorias = jdbc.queryForList(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? AND IS_NULLABLE = 'NO'",
                tabla, columna);
        for (Map<String, Object> col : obligatorias) {
            // tabla y columna salen de la lista fija de arriba, no de datos del usuario
            jdbc.execute("ALTER TABLE " + tabla + " MODIFY " + columna + " " + col.get("COLUMN_TYPE") + " NULL");
            log.info("{}.{} ahora admite NULL (equipos por IMEI)", tabla, columna);
        }
    }
}
