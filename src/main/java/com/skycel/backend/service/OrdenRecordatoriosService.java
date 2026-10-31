package com.skycel.backend.service;

import com.skycel.backend.domain.entity.OrdenServicio;
import com.skycel.backend.repository.OrdenServicioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cada mañana avisa a cada sucursal (en sus avisos del sistema) cuántos equipos listos llevan una semana o más sin que el cliente los recoja
 * y a quién todavía no se le ha recordado. El empleado abre «Por recoger» y manda el WhatsApp ya escrito. No se manda nada al cliente solo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrdenRecordatoriosService {

    private final OrdenServicioRepository ordenRepository;
    private final NotificacionService notificacionService;

    @Scheduled(cron = "${skycel.ordenes.recordatorios.cron:0 0 9 * * *}")
    public void programada() {
        try {
            int n = avisar(LocalDate.now());
            if (n > 0) log.info("Recordatorios de equipos sin recoger: {} sucursal(es) avisadas", n);
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar de los equipos sin recoger", e);
        }
    }

    /** Devuelve a cuántas sucursales se avisó. */
    @Transactional
    public int avisar(LocalDate hoy) {
        Map<Integer, List<OrdenServicio>> porTienda = new TreeMap<>();
        for (OrdenServicio o : ordenRepository.listasSinRecoger(null)) {
            if (OrdenServicioService.diasSinRecoger(o) < OrdenServicioService.DIAS_PARA_RECORDAR) continue;
            // ya se le recordó hace menos de una semana: todavía no toca
            if (o.getUltimoRecordatorio() != null
                    && o.getUltimoRecordatorio().toLocalDate().isAfter(hoy.minusDays(OrdenServicioService.DIAS_PARA_RECORDAR))) continue;
            porTienda.computeIfAbsent(o.getTienda().getCodti(), k -> new java.util.ArrayList<>()).add(o);
        }
        porTienda.forEach((codti, lista) -> {
            long vencidas = lista.stream().filter(o -> OrdenServicioService.diasSinRecoger(o) >= OrdenServicioService.DIAS_PLAZO_RECOGER).count();
            String titulo = lista.size() == 1
                    ? "1 equipo listo lleva " + OrdenServicioService.DIAS_PARA_RECORDAR + " días o más sin recogerse: recuérdaselo al cliente"
                    : lista.size() + " equipos listos llevan " + OrdenServicioService.DIAS_PARA_RECORDAR + " días o más sin recogerse: recuérdaselo a los clientes";
            if (vencidas > 0) titulo += " (" + vencidas + (vencidas == 1 ? " ya pasó" : " ya pasaron") + " de los " + OrdenServicioService.DIAS_PLAZO_RECOGER + " días)";
            notificacionService.notificarTienda(codti, "ORDEN_SIN_RECOGER", titulo, "#/por-recoger");
        });
        return porTienda.size();
    }
}
