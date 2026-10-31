package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Cliente;
import com.skycel.backend.domain.entity.OrdenServicio;
import com.skycel.backend.domain.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Los números del reporte de tiempos del taller. */
class OrdenReporteTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 10, 12, 0);

    private final Usuario juan = Usuario.builder().idusuario(1).nombreCompleto("Juan Pérez").build();
    private final Usuario ana = Usuario.builder().idusuario(2).nombreCompleto("Ana López").build();

    private OrdenServicio orden(String folio, byte estado, Usuario tecnico, LocalDateTime ingreso) {
        return OrdenServicio.builder().folio(folio).estado(estado).tecnico(tecnico).marca("Apple").modelo("iPhone")
                .cliente(Cliente.builder().nombreCompleto("Sofia").build()).fechaIngreso(ingreso).build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Map<String, Object> r, String clave) { return (List<Map<String, Object>>) r.get(clave); }

    @Test
    @DisplayName("tiempos de taller sin contar la espera de refacciones, cumplimiento de la fecha prometida y conteos por estado")
    void tiempos() {
        // 10 h en total, 4 h detenida esperando una pieza: 6 h de trabajo real; lista a tiempo
        OrdenServicio a = orden("OS-1", OrdenServicioService.ENTREGADA, juan, AHORA.minusDays(3));
        a.setFechaLista(a.getFechaIngreso().plusHours(10));
        a.setFechaEntrega(a.getFechaIngreso().plusHours(30));
        a.setEsperaMinutos(240);
        a.setFechaPromesaHora(a.getFechaIngreso().plusHours(12));
        // 20 h, sin espera; se prometió en 8 h: tarde
        OrdenServicio b = orden("OS-2", OrdenServicioService.LISTA, ana, AHORA.minusDays(2));
        b.setFechaLista(b.getFechaIngreso().plusHours(20));
        b.setFechaPromesaHora(b.getFechaIngreso().plusHours(8));
        // cancelada: no cuenta como terminada
        OrdenServicio c = orden("OS-3", OrdenServicioService.CANCELADA, juan, AHORA.minusDays(2));

        Map<String, Object> r = OrdenReporte.calcular(List.of(a, b, c), AHORA);

        assertThat(r).containsEntry("recibidas", 3).containsEntry("entregadas", 1L).containsEntry("canceladas", 1L).containsEntry("abiertas", 1L);
        assertThat(r).containsEntry("terminadas", 2);
        assertThat((Double) r.get("promedioHorasTaller")).isEqualTo(13.0);          // (6 + 20) / 2
        assertThat((Double) r.get("promedioHorasHastaEntrega")).isEqualTo(30.0);
        assertThat(r).containsEntry("conFechaPrometida", 2).containsEntry("aTiempo", 1L).containsEntry("porcentajeATiempo", 50.0);
        assertThat(r).containsEntry("conEsperaDeRefaccion", 1);
        assertThat((Double) r.get("promedioHorasEsperandoRefaccion")).isEqualTo(4.0);
    }

    @Test
    @DisplayName("las retrasadas son las abiertas sin terminar cuya fecha prometida ya pasó; se marca si esperan una refacción")
    void retrasadas() {
        OrdenServicio tarde = orden("OS-4", OrdenServicioService.EN_REPARACION, juan, AHORA.minusDays(5));
        tarde.setFechaPromesaHora(AHORA.minusHours(30));
        OrdenServicio espera = orden("OS-5", OrdenServicioService.EN_REPARACION, ana, AHORA.minusDays(5));
        espera.setFechaPromesa(AHORA.toLocalDate().minusDays(1));   // sin hora: vence al final de ese día
        espera.setEsperaDesde(AHORA.minusDays(1));
        OrdenServicio enTiempo = orden("OS-6", OrdenServicioService.EN_REPARACION, juan, AHORA.minusDays(1));
        enTiempo.setFechaPromesaHora(AHORA.plusHours(5));
        OrdenServicio lista = orden("OS-7", OrdenServicioService.LISTA, juan, AHORA.minusDays(9));
        lista.setFechaPromesaHora(AHORA.minusDays(8));              // ya está lista: no es retraso del taller

        Map<String, Object> r = OrdenReporte.calcular(List.of(tarde, espera, enTiempo, lista), AHORA);

        List<Map<String, Object>> retrasadas = lista(r, "retrasadas");
        assertThat(retrasadas).extracting(m -> m.get("folio")).containsExactly("OS-4", "OS-5");
        assertThat(retrasadas.get(0)).containsEntry("horasDeRetraso", 30.0).containsEntry("esperandoRefaccion", false).containsEntry("tecnico", "Juan Pérez");
        assertThat(retrasadas.get(1)).containsEntry("esperandoRefaccion", true);
        assertThat(r).containsEntry("esperandoRefaccionAhora", 1L);
    }

    @Test
    @DisplayName("por técnico: terminadas, promedio y cumplimiento; sin datos no inventa promedios")
    void porTecnico() {
        OrdenServicio a = orden("OS-8", OrdenServicioService.LISTA, juan, AHORA.minusDays(3));
        a.setFechaLista(a.getFechaIngreso().plusHours(4));
        OrdenServicio b = orden("OS-9", OrdenServicioService.LISTA, juan, AHORA.minusDays(3));
        b.setFechaLista(b.getFechaIngreso().plusHours(8));
        OrdenServicio c = orden("OS-10", OrdenServicioService.LISTA, null, AHORA.minusDays(3));
        c.setFechaLista(c.getFechaIngreso().plusHours(2));

        List<Map<String, Object>> t = lista(OrdenReporte.calcular(List.of(a, b, c), AHORA), "porTecnico");

        assertThat(t).extracting(m -> m.get("tecnico")).containsExactly("Juan Pérez", "Sin técnico");
        assertThat(t.get(0)).containsEntry("terminadas", 2).containsEntry("promedioHorasTaller", 6.0);

        Map<String, Object> vacio = OrdenReporte.calcular(List.of(), AHORA);
        assertThat(vacio.get("promedioHorasTaller")).isNull();
        assertThat(vacio.get("porcentajeATiempo")).isNull();
    }
}
