package com.skycel.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Candado contra el doble clic: una operación idéntica (misma firma) que llega a los pocos segundos de otra se rechaza.
 * Va en memoria y se decide al entrar, antes de tocar la base: dos peticiones que llegan con milisegundos de diferencia
 * todavía no se ven entre sí en la base, pero sí aquí. Un reintento con la misma clave de envío (si trae una) no cuenta como duplicado.
 */
final class AntiDobleEnvio {

    static final long VENTANA_MS = 3_000;

    private record Marca(String clave, long cuando) { }

    private final ConcurrentHashMap<String, Marca> marcas = new ConcurrentHashMap<>();

    /** Reserva la firma; si otra igual (de otra clave) se reservó hace menos de la ventana, rechaza con 409. */
    void reservar(String firma, String clave, String mensaje) {
        long ahora = System.currentTimeMillis();
        marcas.values().removeIf(m -> ahora - m.cuando() > VENTANA_MS);
        boolean[] repetida = {false};
        marcas.compute(firma, (k, previa) -> {
            if (previa != null && ahora - previa.cuando() <= VENTANA_MS && (clave == null || !clave.equals(previa.clave()))) {
                repetida[0] = true;
                return previa;
            }
            return new Marca(clave, ahora);
        });
        if (repetida[0]) throw new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }

    /** La operación falló: se libera la firma para poder reintentarla enseguida. */
    void liberar(String firma) {
        marcas.remove(firma);
    }
}
