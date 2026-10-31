package com.skycel.backend.service;

import com.skycel.backend.domain.entity.OrdenServicio;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cuánto tarda el taller: del ingreso a «lista» y a la entrega (sin contar el tiempo detenido esperando refacciones), cuántas se
 * cumplieron en la fecha prometida, cuáles van retrasadas, cómo le va a cada técnico y cuántos equipos esperan en la tienda.
 */
public final class OrdenReporte {

    private OrdenReporte() {}

    public static Map<String, Object> calcular(List<OrdenServicio> ordenes, LocalDateTime ahora) {
        Map<String, Object> r = new LinkedHashMap<>();

        long entregadas = ordenes.stream().filter(o -> es(o, OrdenServicioService.ENTREGADA)).count();
        long canceladas = ordenes.stream().filter(o -> es(o, OrdenServicioService.CANCELADA)).count();
        long abiertas = ordenes.size() - entregadas - canceladas;
        r.put("recibidas", ordenes.size());
        r.put("entregadas", entregadas);
        r.put("canceladas", canceladas);
        r.put("abiertas", abiertas);

        // Las que ya se terminaron (llegaron a «lista»), sin las canceladas
        List<OrdenServicio> terminadas = ordenes.stream()
                .filter(o -> o.getFechaLista() != null && !es(o, OrdenServicioService.CANCELADA)).collect(Collectors.toList());
        r.put("terminadas", terminadas.size());
        r.put("promedioHorasTaller", promedio(terminadas.stream().map(OrdenReporte::horasTaller).collect(Collectors.toList())));
        r.put("promedioHorasHastaEntrega", promedio(ordenes.stream()
                .filter(o -> es(o, OrdenServicioService.ENTREGADA) && o.getFechaEntrega() != null && o.getFechaIngreso() != null)
                .map(o -> horas(Duration.between(o.getFechaIngreso(), o.getFechaEntrega()).toMinutes())).collect(Collectors.toList())));

        // Cumplimiento de la fecha prometida
        List<OrdenServicio> conPromesa = terminadas.stream().filter(o -> promesa(o) != null).collect(Collectors.toList());
        long aTiempo = conPromesa.stream().filter(o -> !o.getFechaLista().isAfter(promesa(o))).count();
        r.put("conFechaPrometida", conPromesa.size());
        r.put("aTiempo", aTiempo);
        r.put("porcentajeATiempo", conPromesa.isEmpty() ? null : Math.round(aTiempo * 1000.0 / conPromesa.size()) / 10.0);

        // Esperas por refacción
        List<Double> esperas = ordenes.stream().filter(o -> o.getEsperaMinutos() != null && o.getEsperaMinutos() > 0)
                .map(o -> horas(o.getEsperaMinutos())).collect(Collectors.toList());
        r.put("conEsperaDeRefaccion", esperas.size());
        r.put("promedioHorasEsperandoRefaccion", promedio(esperas));
        r.put("esperandoRefaccionAhora", ordenes.stream().filter(o -> o.getEsperaDesde() != null && abierta(o)).count());

        // Retrasadas: aún sin terminar y ya pasó la fecha prometida
        List<Map<String, Object>> retrasadas = ordenes.stream()
                .filter(o -> (es(o, OrdenServicioService.RECIBIDA) || es(o, OrdenServicioService.EN_REPARACION)) && promesa(o) != null && promesa(o).isBefore(ahora))
                .sorted(Comparator.comparing(OrdenReporte::promesa))
                .limit(20)
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("folio", o.getFolio());
                    m.put("equipo", (nz(o.getMarca()) + " " + nz(o.getModelo())).trim());
                    m.put("cliente", o.getCliente() != null ? o.getCliente().getNombreCompleto() : null);
                    m.put("tecnico", o.getTecnico() != null ? o.getTecnico().getNombreCompleto() : null);
                    m.put("horasDeRetraso", horas(Duration.between(promesa(o), ahora).toMinutes()));
                    m.put("esperandoRefaccion", o.getEsperaDesde() != null);
                    return m;
                }).collect(Collectors.toList());
        r.put("retrasadas", retrasadas);

        // Por técnico (las que terminó)
        Map<String, List<OrdenServicio>> porTecnico = new TreeMap<>();
        for (OrdenServicio o : terminadas) {
            porTecnico.computeIfAbsent(o.getTecnico() != null ? o.getTecnico().getNombreCompleto() : "Sin técnico", k -> new ArrayList<>()).add(o);
        }
        List<Map<String, Object>> tecnicos = new ArrayList<>();
        porTecnico.forEach((nombre, lista) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tecnico", nombre);
            m.put("terminadas", lista.size());
            m.put("promedioHorasTaller", promedio(lista.stream().map(OrdenReporte::horasTaller).collect(Collectors.toList())));
            long cumplidas = lista.stream().filter(o -> promesa(o) != null && !o.getFechaLista().isAfter(promesa(o))).count();
            long conFecha = lista.stream().filter(o -> promesa(o) != null).count();
            m.put("aTiempo", cumplidas);
            m.put("conFechaPrometida", conFecha);
            tecnicos.add(m);
        });
        tecnicos.sort((a, b) -> Integer.compare((Integer) b.get("terminadas"), (Integer) a.get("terminadas")));
        r.put("porTecnico", tecnicos);
        return r;
    }

    /** Horas de trabajo real: del ingreso a «lista», sin el tiempo que estuvo detenida esperando una refacción. */
    static double horasTaller(OrdenServicio o) {
        if (o.getFechaIngreso() == null || o.getFechaLista() == null) return 0;
        long minutos = Duration.between(o.getFechaIngreso(), o.getFechaLista()).toMinutes() - (o.getEsperaMinutos() == null ? 0 : o.getEsperaMinutos());
        return horas(Math.max(0, minutos));
    }

    /** Cuándo se le prometió al cliente: la hora exacta si hay, o el final del día prometido. */
    static LocalDateTime promesa(OrdenServicio o) {
        if (o.getFechaPromesaHora() != null) return o.getFechaPromesaHora();
        return o.getFechaPromesa() != null ? o.getFechaPromesa().atTime(23, 59) : null;
    }

    private static boolean es(OrdenServicio o, byte estado) { return o.getEstado() != null && o.getEstado() == estado; }

    private static boolean abierta(OrdenServicio o) {
        return es(o, OrdenServicioService.RECIBIDA) || es(o, OrdenServicioService.EN_REPARACION) || es(o, OrdenServicioService.LISTA);
    }

    private static double horas(long minutos) { return Math.round(minutos / 6.0) / 10.0; }

    private static Double promedio(List<Double> v) {
        return v.isEmpty() ? null : Math.round(v.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
