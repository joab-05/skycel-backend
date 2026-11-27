package com.skycel.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Restricciones del tipo de producto")
class TipoProductoCheckInitializerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TipoProductoCheckInitializer inicio = new TipoProductoCheckInitializer(jdbc);

    @Test
    @DisplayName("ensancha a 0..4 las restricciones que solo admitían hasta 3 y deja las demás")
    void ensancha() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("tabla", "producto_master_aud", "nombre", "producto_master_aud_chk_1", "clausula", "(`tipo` between 0 and 3)"),
                Map.of("tabla", "descuento_regla", "nombre", "descuento_regla_chk_1", "clausula", "(`tipo_producto` between 0 and 3)"),
                Map.of("tabla", "otra", "nombre", "otra_chk_1", "clausula", "(`tipo` between 0 and 4)"),
                Map.of("tabla", "movimiento", "nombre", "movimiento_chk_1", "clausula", "(`tipo` in (1,2))")));

        inicio.run();

        verify(jdbc).execute("ALTER TABLE `producto_master_aud` DROP CHECK `producto_master_aud_chk_1`");
        verify(jdbc).execute("ALTER TABLE `producto_master_aud` ADD CONSTRAINT `producto_master_aud_chk_1` CHECK (`tipo` between 0 and 4)");
        verify(jdbc).execute("ALTER TABLE `descuento_regla` DROP CHECK `descuento_regla_chk_1`");
        verify(jdbc).execute("ALTER TABLE `descuento_regla` ADD CONSTRAINT `descuento_regla_chk_1` CHECK (`tipo_producto` between 0 and 4)");
        verify(jdbc, times(4)).execute(anyString());   // las otras dos no se tocaron
    }

    @Test
    @DisplayName("si la base no responde, no impide arrancar")
    void nuncaFalla() {
        when(jdbc.queryForList(anyString())).thenThrow(new IllegalStateException("sin información"));

        inicio.run();

        verify(jdbc, never()).execute(anyString());
    }
}
