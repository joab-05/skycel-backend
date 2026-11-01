package com.skycel.backend.config;

import com.skycel.backend.service.CompraService;
import com.skycel.backend.service.CuentaPorCobrarService;
import com.skycel.backend.service.RespaldoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

class TareasAlArrancarTest {

    private final RespaldoService respaldo = mock(RespaldoService.class);
    private final CuentaPorCobrarService cuentas = mock(CuentaPorCobrarService.class);
    private final CompraService compras = mock(CompraService.class);
    private final TareasAlArrancar tareas = new TareasAlArrancar(respaldo, cuentas, compras, 0);

    @Test
    @DisplayName("al encender el servidor hace el respaldo que faltó y marca lo vencido")
    void poneAlCorriente() {
        when(respaldo.activo()).thenReturn(true);
        when(respaldo.hayRespaldoReciente()).thenReturn(false);
        when(respaldo.ejecutar()).thenReturn(Map.of("ok", true));

        tareas.ponerAlCorriente();

        verify(respaldo).ejecutar();
        verify(cuentas).marcarVencidas();
        verify(compras).marcarVencidas();
    }

    @Test
    @DisplayName("si ya hay un respaldo reciente (o el respaldo no está activo) no lo repite")
    void noRepite() {
        when(respaldo.activo()).thenReturn(true);
        when(respaldo.hayRespaldoReciente()).thenReturn(true);
        tareas.ponerAlCorriente();
        when(respaldo.activo()).thenReturn(false);
        tareas.ponerAlCorriente();

        verify(respaldo, never()).ejecutar();
        verify(cuentas, times(2)).marcarVencidas();
    }

    @Test
    @DisplayName("un paso que falla no impide los demás")
    void independientes() {
        when(respaldo.activo()).thenReturn(true);
        when(respaldo.hayRespaldoReciente()).thenReturn(false);
        when(respaldo.ejecutar()).thenThrow(new IllegalStateException("sin mysqldump"));
        doThrow(new IllegalStateException("bd")).when(cuentas).marcarVencidas();

        tareas.ponerAlCorriente();

        verify(compras).marcarVencidas();
    }
}
