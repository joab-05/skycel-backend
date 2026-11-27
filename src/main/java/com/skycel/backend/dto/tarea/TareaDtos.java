package com.skycel.backend.dto.tarea;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Lo que entra y sale de la API de tareas. */
public final class TareaDtos {

    private TareaDtos() {
    }

    /** Alta y cambio de una actividad de la rutina. {@code codti} vacío = todas las tiendas; {@code dias} (1 = lunes ... 7 = domingo) vacío = todos. */
    public record RutinaRequest(Integer codti, String titulo, String descripcion, String momento, Integer orden,
                                String rolDestino, List<Integer> dias, String horaLimite, Boolean activa) {
    }

    public record RutinaDto(Integer idrutina, Integer codti, String nombreTienda, String titulo, String descripcion,
                            String momento, String momentoEtiqueta, Integer orden, String rolDestino, List<Integer> dias,
                            String horaLimite, boolean activa) {
    }

    public record NotaRequest(String nota) {
    }

    public record TareaDto(Integer idtarea, Integer codti, LocalDate fecha, String titulo, String descripcion, String momento,
                           String momentoEtiqueta, Integer orden, String rolDestino, String horaLimite, String estado, String nota,
                           Integer idusuarioHecha, String hechaPor, LocalDateTime hechaEl, boolean tarde, boolean atrasada) {
    }

    public record TareasDiaDto(Integer codti, String nombreTienda, LocalDate fecha, int total, int hechas, int pendientes,
                               int atrasadas, List<TareaDto> tareas) {
    }

    public record TiendaCumplimiento(Integer codti, String nombre, int total, int hechas, int aTiempo, int noHechas,
                                     int pendientesHoy, Integer pctHechas, Integer pctATiempo) {
    }

    public record PersonaCumplimiento(Integer idusuario, String nombre, int hechas, int tarde) {
    }

    public record DiaCumplimiento(LocalDate fecha, int total, int hechas) {
    }

    public record CumplimientoDto(LocalDate desde, LocalDate hasta, Integer codti, List<TiendaCumplimiento> tiendas,
                                  List<PersonaCumplimiento> personas, List<DiaCumplimiento> dias) {
    }
}
