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
 * Las garantías de productos vendidos con el sistema anterior no tienen venta ni producto aquí, así que
 * {@code garantia.idventa} y {@code garantia.idproducto} dejaron de ser obligatorias. {@code ddl-auto: update} agrega
 * columnas nuevas pero no quita el NOT NULL de una existente: este arranque lo hace una sola vez y, si ya está hecho, no
 * toca nada (se puede correr las veces que sea).
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class GarantiaEsquemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> obligatorias = jdbc.queryForList(
                    "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'garantia' " +
                    "AND COLUMN_NAME IN ('idventa', 'idproducto') AND IS_NULLABLE = 'NO'");
            for (Map<String, Object> col : obligatorias) {
                String nombre = String.valueOf(col.get("COLUMN_NAME"));
                String tipo = String.valueOf(col.get("COLUMN_TYPE"));
                // los dos nombres salen de la lista fija de arriba, no de datos del usuario
                jdbc.execute("ALTER TABLE garantia MODIFY " + nombre + " " + tipo + " NULL");
                log.info("garantia.{} ahora admite NULL (garantías de ventas del sistema anterior)", nombre);
            }
        } catch (Exception e) {
            // Otra base de datos (pruebas) o la tabla aún no existe: no impide arrancar
            log.warn("No se pudo ajustar el esquema de garantia: {}", e.getMessage());
        }
    }
}
