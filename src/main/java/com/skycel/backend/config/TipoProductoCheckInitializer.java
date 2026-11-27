package com.skycel.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Los tipos de producto se guardan por su posición (0 = celular ... 3 = tablet) y Hibernate crea, junto a cada columna, una restricción
 * {@code CHECK (tipo between 0 and 3)}; {@code ddl-auto: update} no la modifica cuando se agrega un tipo, así que «Refacción» (4) se
 * rechazaría. Este arranque, que se puede correr las veces que sea, ensancha esas restricciones a {@code between 0 and 4} (en el artículo, su
 * auditoría, las categorías y las reglas de descuento) antes de que la migración de refacciones guarde el tipo nuevo.
 */
@Slf4j
@Component
@Order(9)
@RequiredArgsConstructor
public class TipoProductoCheckInitializer implements CommandLineRunner {

    private static final Pattern ANGOSTA = Pattern.compile("`(tipo|tipo_producto)`\\s+between\\s+0\\s+and\\s+3", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT tc.TABLE_NAME AS tabla, cc.CONSTRAINT_NAME AS nombre, cc.CHECK_CLAUSE AS clausula "
                            + "FROM information_schema.CHECK_CONSTRAINTS cc "
                            + "JOIN information_schema.TABLE_CONSTRAINTS tc ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME "
                            + "WHERE cc.CONSTRAINT_SCHEMA = DATABASE() AND tc.CONSTRAINT_TYPE = 'CHECK' "
                            + "AND (cc.CHECK_CLAUSE LIKE '%`tipo`%' OR cc.CHECK_CLAUSE LIKE '%`tipo_producto`%')");
            for (Map<String, Object> f : filas) {
                String tabla = String.valueOf(f.get("tabla"));
                String nombre = String.valueOf(f.get("nombre"));
                String clausula = String.valueOf(f.get("clausula"));
                if (!ANGOSTA.matcher(clausula).find()) continue;
                String nueva = clausula.replaceAll("(?i)between\\s+0\\s+and\\s+3", "between 0 and 4");
                jdbc.execute("ALTER TABLE `" + tabla + "` DROP CHECK `" + nombre + "`");
                jdbc.execute("ALTER TABLE `" + tabla + "` ADD CONSTRAINT `" + nombre + "` CHECK " + nueva);
                log.info("Tipo de producto: la restricción {} de {} ahora admite hasta 4 (Refacción)", nombre, tabla);
            }
        } catch (Exception e) {
            // Otra base de datos (pruebas) o sin esa información: no impide arrancar
            log.warn("No se pudieron ensanchar las restricciones del tipo de producto: {}", e.getMessage());
        }
    }
}
