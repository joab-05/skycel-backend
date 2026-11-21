package com.skycel.backend.config;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * El encargado actual de cada tienda (y el taller de los técnicos) ahora se designa en la tienda y no en cada empleado. Este arranque,
 * que se puede correr las veces que sea y no pisa nada ya designado:
 * <ol>
 *   <li>deja que {@code usuario.codti} admita NULL ({@code ddl-auto: update} no quita un NOT NULL ya existente): los vendedores y
 *       administradores nuevos no tienen tienda fija;</li>
 *   <li>a cada tienda sin encargado le pone el encargado que ya tenía asignado (si hay varios, el de menor id);</li>
 *   <li>si ninguna tienda está marcada como taller, marca la que ya tiene a los técnicos.</li>
 * </ol>
 */
@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class EncargadosTiendaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final TiendaRepository tiendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransactionTemplate transaccion;

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> obligatorias = jdbc.queryForList(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'codti' AND IS_NULLABLE = 'NO'");
            if (!obligatorias.isEmpty()) {
                jdbc.execute("ALTER TABLE usuario MODIFY codti " + obligatorias.get(0).get("COLUMN_TYPE") + " NULL");
                log.info("usuario.codti ahora admite NULL (la tienda se designa en la tienda, no en el empleado)");
            }
            transaccion.executeWithoutResult(s -> designarLoQueYaEstaba());
        } catch (Exception e) {
            // Otra base de datos (pruebas) o las tablas aún no existen: no impide arrancar
            log.warn("No se pudo preparar el encargado por tienda: {}", e.getMessage());
        }
    }

    private void designarLoQueYaEstaba() {
        List<Usuario> activos = usuarioRepository.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getActivo())).toList();
        List<Tienda> tiendas = tiendaRepository.findAll();

        for (Tienda t : tiendas) {
            if (t.getIdEncargado() != null) continue;
            activos.stream()
                    .filter(u -> u.getRol() == Rol.ENCARGADO_TIENDA && u.getTienda() != null && u.getTienda().getCodti().equals(t.getCodti()))
                    .filter(u -> tiendas.stream().noneMatch(o -> u.getIdusuario().equals(o.getIdEncargado())))   // ya es de otra tienda
                    .min(Comparator.comparing(Usuario::getIdusuario))
                    .ifPresent(u -> {
                        t.setIdEncargado(u.getIdusuario());
                        tiendaRepository.save(t);
                        log.info("Encargado de {}: {}", t.getNombre(), u.getNombreCompleto());
                    });
        }

        if (tiendas.stream().noneMatch(t -> Boolean.TRUE.equals(t.getEsTaller()))) {
            Map<Integer, Long> tecnicosPorTienda = activos.stream()
                    .filter(u -> u.getRol() == Rol.TECNICO && u.getTienda() != null)
                    .collect(Collectors.groupingBy(u -> u.getTienda().getCodti(), Collectors.counting()));
            tecnicosPorTienda.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e ->
                    tiendas.stream().filter(t -> t.getCodti().equals(e.getKey())).findFirst().ifPresent(t -> {
                        t.setEsTaller(true);
                        tiendaRepository.save(t);
                        log.info("Taller de los técnicos: {}", t.getNombre());
                    }));
        }
    }
}
