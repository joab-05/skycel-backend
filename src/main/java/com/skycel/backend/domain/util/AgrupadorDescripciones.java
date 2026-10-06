package com.skycel.backend.domain.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Separa la primera parte de una descripción para usarla como subcategoría 2 ("de Pared Iphone Tipo C 20w" →
 * subcategoría "Pared" + descripción "Iphone Tipo C 20w").
 *
 * La primera parte es la primera palabra (sin los conectores del inicio: de, del, para, con...). Se hacen grupos por esa
 * palabra y solo cuentan los grupos con al menos {@code minimo} descripciones, para no crear subcategorías de un solo
 * artículo. Si casi todas las descripciones de un grupo comparten también la palabra siguiente, esa va con el grupo
 * ("Uso Rudo", "Silver Case"); las que no la comparten se agrupan solo por la primera palabra.
 */
public final class AgrupadorDescripciones {

    private static final Set<String> CONECTORES = Set.of("de", "del", "para", "con", "y", "el", "la", "los", "las", "en", "p/", "c/");
    /** Parte de las descripciones de un grupo que debe compartir la palabra siguiente para sumarla al nombre del grupo. */
    private static final double COMPARTIDA = 0.9;
    private static final int MIN_PARA_EXTENDER = 3;

    private AgrupadorDescripciones() {}

    /** Subcategoría que le toca a la descripción y lo que queda como descripción (null si no queda nada). */
    public record Asignacion(String subcategoria, String resto) {}

    private record Item(String original, List<String> palabras) {}

    public static Map<String, Asignacion> agrupar(Collection<String> descripciones, int minimo) {
        Map<String, List<Item>> porPrimera = new LinkedHashMap<>();
        for (String d : descripciones) {
            if (d == null) continue;
            List<String> t = palabras(d);
            if (t.isEmpty()) continue;
            porPrimera.computeIfAbsent(t.get(0).toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(new Item(d, t));
        }
        Map<String, Asignacion> resultado = new HashMap<>();
        for (List<Item> grupo : porPrimera.values()) {
            if (grupo.size() < minimo) continue;
            int n = palabrasDelNombre(grupo);
            String prefijo = prefijoMasComun(grupo, n);
            List<Item> coinciden = new ArrayList<>();
            List<Item> otras = new ArrayList<>();
            for (Item i : grupo) {
                (i.palabras().size() >= n && prefijoDe(i.palabras(), n).equals(prefijo) ? coinciden : otras).add(i);
            }
            asignar(coinciden, n, resultado);
            if (n > 1 && otras.size() >= minimo) asignar(otras, 1, resultado);
        }
        return resultado;
    }

    private static void asignar(List<Item> items, int n, Map<String, Asignacion> destino) {
        if (items.isEmpty()) return;
        List<String> primera = items.get(0).palabras();
        String nombre = String.join(" ", primera.subList(0, Math.min(n, primera.size())));
        for (Item i : items) {
            List<String> p = i.palabras();
            String resto = p.size() > n ? String.join(" ", p.subList(n, p.size())) : null;
            destino.put(i.original(), new Asignacion(nombre, resto));
        }
    }

    /** Cuántas palabras lleva el nombre del grupo: la primera, más las siguientes mientras las compartan casi todos. */
    private static int palabrasDelNombre(List<Item> grupo) {
        int n = 1;
        if (grupo.size() < MIN_PARA_EXTENDER) return n;
        while (true) {
            final int largo = n + 1;
            int mejor = frecuenciaDelPrefijoMasComun(grupo, largo);
            if (mejor >= COMPARTIDA * grupo.size()) n++; else break;
        }
        return n;
    }

    private static int frecuenciaDelPrefijoMasComun(List<Item> grupo, int n) {
        Map<String, Integer> cuenta = new HashMap<>();
        for (Item i : grupo) {
            if (i.palabras().size() >= n) cuenta.merge(prefijoDe(i.palabras(), n), 1, Integer::sum);
        }
        return cuenta.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static String prefijoMasComun(List<Item> grupo, int n) {
        Map<String, Integer> cuenta = new HashMap<>();
        for (Item i : grupo) {
            if (i.palabras().size() >= n) cuenta.merge(prefijoDe(i.palabras(), n), 1, Integer::sum);
        }
        return cuenta.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private static String prefijoDe(List<String> p, int n) {
        return String.join(" ", p.subList(0, Math.min(n, p.size()))).toLowerCase(Locale.ROOT);
    }

    /** Palabras de la descripción sin los conectores del inicio. */
    static List<String> palabras(String d) {
        String[] t = d.trim().split("\\s+");
        int i = 0;
        while (i < t.length && CONECTORES.contains(t[i].toLowerCase(Locale.ROOT))) i++;
        List<String> r = new ArrayList<>();
        for (; i < t.length; i++) if (!t[i].isEmpty()) r.add(t[i]);
        return r;
    }
}
