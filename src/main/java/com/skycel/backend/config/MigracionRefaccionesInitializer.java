package com.skycel.backend.config;

import com.skycel.backend.dto.producto.MigracionRefaccionesDto;
import com.skycel.backend.service.MigracionRefaccionesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Una sola vez, la primera vez que arranca el servidor con el tipo «Refacción»: pasa a ese tipo los productos de tipo accesorio del taller
 * (ver {@link MigracionRefaccionesService}). La marca de que ya se hizo es que exista la categoría principal «Refacciones» de tipo
 * Refacción, que la propia migración crea en la misma transacción (todo o nada). Un fallo no impide arrancar: queda en el log y se reintenta
 * en el siguiente arranque.
 */
@Slf4j
@Component
@Order(12)
@RequiredArgsConstructor
public class MigracionRefaccionesInitializer implements CommandLineRunner {

    private final MigracionRefaccionesService migracion;

    @Override
    public void run(String... args) {
        try {
            if (migracion.yaSeHizo()) return;
            MigracionRefaccionesDto r = migracion.aplicar();
            log.info("Refacciones: se pasaron {} artículos del taller ({} productos, {} con existencia) al tipo Refacción; {} categorías nuevas; "
                            + "{} artículos compartidos con otras tiendas no se tocaron.",
                    r.articulos(), r.productos(), r.productosConExistencia(), r.categoriasNuevas().size(), r.omitidos().size());
            r.categoriasNuevas().forEach(c -> log.info("Refacciones: categoría nueva {}", c));
            r.omitidos().forEach(o -> log.warn("Refacciones: no se tocó (también está en otra tienda): {}", o));
        } catch (Exception e) {
            // Otra base de datos (pruebas), sin tienda taller o con un conflicto: no impide arrancar
            log.warn("No se pudo migrar las refacciones del taller: {}", e.getMessage());
        }
    }
}
