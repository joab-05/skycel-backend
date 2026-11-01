package com.skycel.backend.service;

import com.skycel.backend.domain.enums.Rol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Limpieza automática de lo que no hace falta guardar para siempre. Cada noche (después del respaldo) borra:
 * las sesiones de entrada viejas, los avisos ya leídos, el historial de cambios del catálogo (auditoría) más antiguo que el plazo y las
 * fotos de las reparaciones ya entregadas o canceladas.
 * Las ventas, la caja, las compras, la nómina y todo lo contable NUNCA se tocan. Si el respaldo de la noche no se pudo hacer, la limpieza
 * no corre.
 */
@Slf4j
@Service
public class LimpiezaService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final RespaldoService respaldoService;
    private final NotificacionService notificacionService;
    private final boolean activo;
    private final int diasSesiones;
    private final int diasAvisos;
    private final int mesesHistorial;
    private final int diasFotos;

    private final ReentrantLock candado = new ReentrantLock();
    private volatile Map<String, Object> ultimo;

    @Autowired
    public LimpiezaService(JdbcTemplate jdbc, TransactionTemplate transaccion, RespaldoService respaldoService, NotificacionService notificacionService,
                           @Value("${skycel.limpieza.activo:true}") boolean activo,
                           @Value("${skycel.limpieza.dias-sesiones:30}") int diasSesiones,
                           @Value("${skycel.limpieza.dias-avisos:90}") int diasAvisos,
                           @Value("${skycel.limpieza.meses-historial:24}") int mesesHistorial,
                           @Value("${skycel.limpieza.dias-fotos:90}") int diasFotos) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.respaldoService = respaldoService;
        this.notificacionService = notificacionService;
        this.activo = activo;
        this.diasSesiones = Math.max(7, diasSesiones);
        this.diasAvisos = Math.max(30, diasAvisos);
        this.mesesHistorial = Math.max(1, mesesHistorial);
        this.diasFotos = Math.max(30, diasFotos);
    }

    // Después del respaldo de las 23:30 y antes de que el servidor se apague (24:00)
    @Scheduled(cron = "${skycel.limpieza.cron:0 45 23 * * *}")
    public void programada() {
        if (!activo) return;
        if (!respaldoService.hayRespaldoReciente()) {
            log.warn("Limpieza omitida: no hay un respaldo de las últimas 26 horas.");
            return;
        }
        Map<String, Object> r = ejecutar();
        if (!Boolean.TRUE.equals(r.get("ok"))) {
            log.error("La limpieza automática falló: {}", r.get("error"));
            try {
                String msg = "La limpieza automática de la base de datos falló: " + r.get("error");
                notificacionService.notificarRol(Rol.ROOT, "LIMPIEZA_FALLO", msg, "#/inicio");
            } catch (RuntimeException e) {
                log.warn("No se pudo avisar del fallo de la limpieza", e);
            }
        } else {
            log.info("Limpieza automática lista: {}", r);
        }
    }

    public Map<String, Object> ejecutar() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fecha", LocalDateTime.now().toString());
        if (!candado.tryLock()) {
            r.put("ok", false);
            r.put("error", "Ya hay una limpieza en curso.");
            return r;
        }
        try {
            LocalDateTime ahora = LocalDateTime.now();
            long corteHistorial = ahora.minusMonths(mesesHistorial).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            Map<String, Object> borrado = transaccion.execute(status -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sesiones", jdbc.update("DELETE FROM usuario_sesion WHERE fecha_ingreso < ?", ahora.minusDays(diasSesiones)));
                m.put("avisosLeidos", jdbc.update("DELETE FROM notificacion WHERE leida = 1 AND fecha_creacion < ?", ahora.minusDays(diasAvisos)));
                m.put("historialCambios", borrarHistorial(corteHistorial));
                m.put("fotosDeReparaciones", jdbc.update("DELETE f FROM orden_servicio_foto f JOIN orden_servicio o ON o.idorden = f.idorden "
                        + "WHERE o.estado IN (4, 5) AND o.fecha_actualizacion < ?", ahora.minusDays(diasFotos)));
                return m;
            });
            r.put("borrado", borrado);
            r.put("ok", true);
        } catch (RuntimeException e) {
            r.put("ok", false);
            r.put("error", e.getMessage());
        } finally {
            candado.unlock();
        }
        ultimo = r;
        return r;
    }

    /**
     * El historial de cambios (tablas *_AUD de Envers) más viejo que el corte. Las tablas que apuntan a REVINFO se descubren solas, así que
     * una entidad auditada nueva no se queda atrás ni rompe la limpieza.
     */
    private int borrarHistorial(long corteMs) {
        List<Map<String, Object>> tablas = jdbc.queryForList(
                "SELECT table_name AS t, column_name AS c FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND referenced_table_name = 'revinfo'");
        int total = 0;
        for (Map<String, Object> f : tablas) {
            String t = String.valueOf(f.get("t")), c = String.valueOf(f.get("c"));
            if (!t.matches("[A-Za-z0-9_]+") || !c.matches("[A-Za-z0-9_]+")) continue;   // solo nombres normales
            total += jdbc.update("DELETE a FROM `" + t + "` a JOIN revinfo r ON r.rev = a.`" + c + "` WHERE r.revtstmp < ?", corteMs);
        }
        jdbc.update("DELETE FROM revinfo WHERE revtstmp < ?", corteMs);
        return total;
    }

    public Map<String, Object> estado() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activo", activo);
        m.put("conservaSesionesDias", diasSesiones);
        m.put("conservaAvisosLeidosDias", diasAvisos);
        m.put("conservaHistorialMeses", mesesHistorial);
        m.put("conservaFotosDeReparacionesCerradasDias", diasFotos);
        m.put("ultimaLimpieza", ultimo);
        return m;
    }
}
