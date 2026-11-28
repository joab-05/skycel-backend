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
 * Hibernate guarda el estado de una tarea ({@code @Enumerated(STRING)}) como una lista cerrada de MySQL ({@code enum('HECHA','PENDIENTE')}) y
 * {@code ddl-auto: update} no la modifica cuando se agregan estados (por revisar, aprobada), que entonces se rechazarían. Este arranque, que
 * se puede correr las veces que sea, la convierte en un texto normal ({@code VARCHAR(20)}) sin tocar los datos.
 */
@Slf4j
@Component
@Order(8)
@RequiredArgsConstructor
public class TareaEstadoInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> filas = jdbc.queryForList("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'tarea' AND COLUMN_NAME = 'estado' AND COLUMN_TYPE LIKE 'enum%'");
            if (filas.isEmpty()) return;
            jdbc.execute("ALTER TABLE tarea MODIFY estado VARCHAR(20) NOT NULL");
            log.info("Tareas: la columna estado ahora es texto (admite por revisar y aprobada)");
        } catch (Exception e) {
            // Otra base de datos (pruebas) o la tabla aún no existe: no impide arrancar
            log.warn("No se pudo preparar la columna estado de las tareas: {}", e.getMessage());
        }
    }
}
