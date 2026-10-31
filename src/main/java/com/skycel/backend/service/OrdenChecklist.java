package com.skycel.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La revisión rápida del equipo al recibirlo: cada punto se marca OK, FALLA o SIN_PROBAR (por ejemplo, sin batería no se prueba nada).
 * Sirve para deslindar responsabilidad: se imprime en el comprobante y se compara al entregar.
 */
public final class OrdenChecklist {

    /** Los puntos de la revisión, en orden, con el texto que ve el empleado. Las pantallas usan estas mismas claves. */
    public static final Map<String, String> PUNTOS = new LinkedHashMap<>();
    static {
        PUNTOS.put("enciende", "Enciende");
        PUNTOS.put("pantalla", "Pantalla y táctil");
        PUNTOS.put("botones", "Botones");
        PUNTOS.put("camaras", "Cámaras");
        PUNTOS.put("audio", "Bocina y micrófono");
        PUNTOS.put("carga", "Carga");
        PUNTOS.put("senal", "Señal y Wi-Fi");
        PUNTOS.put("humedad", "Sin rastros de humedad");
    }

    public static final String OK = "OK", FALLA = "FALLA", SIN_PROBAR = "SIN_PROBAR";

    private static final ObjectMapper JSON = new ObjectMapper();

    private OrdenChecklist() {}

    /** Valida y limpia lo que mandó la pantalla; devuelve null si no marcó nada. */
    public static Map<String, String> validar(Map<String, String> pedido) {
        if (pedido == null || pedido.isEmpty()) return null;
        Map<String, String> limpio = new LinkedHashMap<>();
        for (String clave : PUNTOS.keySet()) {            // en el orden de la revisión
            String v = pedido.get(clave);
            if (v == null || v.isBlank()) continue;
            String valor = v.trim().toUpperCase();
            if (!valor.equals(OK) && !valor.equals(FALLA) && !valor.equals(SIN_PROBAR)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisión del equipo: «" + PUNTOS.get(clave) + "» debe ser OK, FALLA o SIN_PROBAR.");
            }
            limpio.put(clave, valor);
        }
        for (String clave : pedido.keySet()) {
            if (!PUNTOS.containsKey(clave)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revisión del equipo: punto desconocido «" + clave + "».");
            }
        }
        return limpio.isEmpty() ? null : limpio;
    }

    public static String aJson(Map<String, String> checklist) {
        if (checklist == null || checklist.isEmpty()) return null;
        try { return JSON.writeValueAsString(checklist); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Lo guardado, o un mapa vacío si no hay nada o está dañado. */
    public static Map<String, String> deJson(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try { return JSON.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }
}
