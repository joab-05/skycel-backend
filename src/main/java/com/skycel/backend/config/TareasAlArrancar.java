package com.skycel.backend.config;

import com.skycel.backend.service.CompraService;
import com.skycel.backend.service.CuentaPorCobrarService;
import com.skycel.backend.service.RespaldoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * El servidor no está encendido todo el día (de 7:00 a 24:00): lo que se programa para la madrugada no ocurriría nunca. Por eso los
 * respaldos y las limpiezas van en la noche, y al encenderse el servidor se ponen al corriente las tareas que dependen de la fecha:
 * un respaldo si el último tiene más de 26 horas, y marcar las cuentas por cobrar y las compras que ya vencieron.
 */
@Slf4j
@Component
public class TareasAlArrancar {

    private final RespaldoService respaldoService;
    private final CuentaPorCobrarService cuentaPorCobrarService;
    private final CompraService compraService;
    private final long esperaSegundos;

    public TareasAlArrancar(RespaldoService respaldoService, CuentaPorCobrarService cuentaPorCobrarService, CompraService compraService,
                            @Value("${skycel.arranque.espera-segundos:180}") long esperaSegundos) {
        this.respaldoService = respaldoService;
        this.cuentaPorCobrarService = cuentaPorCobrarService;
        this.compraService = compraService;
        this.esperaSegundos = Math.max(0, esperaSegundos);
    }

    /** Pasados unos minutos del arranque (para no competir con las primeras conexiones), sin detener el arranque. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(esperaSegundos * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            ponerAlCorriente();
        }, "tareas-al-arrancar");
        t.setDaemon(true);
        t.start();
    }

    /** Hace lo que no pudo hacerse mientras el servidor estaba apagado. Cada paso es independiente: uno que falle no impide los demás. */
    public void ponerAlCorriente() {
        try {
            if (respaldoService.activo() && !respaldoService.hayRespaldoReciente()) {
                log.info("Al arrancar: no hay un respaldo de las últimas 26 horas, se hace ahora.");
                Map<String, Object> r = respaldoService.ejecutar();
                if (!Boolean.TRUE.equals(r.get("ok"))) log.error("El respaldo al arrancar falló: {}", r.get("error"));
            }
        } catch (RuntimeException e) {
            log.error("No se pudo poner al corriente el respaldo", e);
        }
        try {
            cuentaPorCobrarService.marcarVencidas();
        } catch (RuntimeException e) {
            log.error("No se pudieron marcar las cuentas por cobrar vencidas", e);
        }
        try {
            compraService.marcarVencidas();
        } catch (RuntimeException e) {
            log.error("No se pudieron marcar las compras vencidas", e);
        }
    }
}
