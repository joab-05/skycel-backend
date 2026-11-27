package com.skycel.backend.controller;

import com.skycel.backend.dto.tarea.TareaDtos.*;
import com.skycel.backend.service.TareaService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Rutinas de actividades y tareas del día de cada tienda. */
@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Tareas", description = "Rutinas diarias por tienda, tareas del día y su cumplimiento")
public class TareaController {

    private final TareaService service;

    // ── El personal ────────────────────────────────────────────────────────────

    @GetMapping("/mias")
    @PreAuthorize("@permisos.tiene('TAREAS_VER')")
    public ResponseEntity<StandardApiResponse<TareasDiaDto>> mias(@RequestParam Integer codti,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.mias(codti, userDetails.getUsername()), "tareas");
    }

    @PostMapping("/{idtarea}/hecha")
    @PreAuthorize("@permisos.tiene('TAREAS_VER')")
    public ResponseEntity<StandardApiResponse<TareaDto>> marcarHecha(@PathVariable Integer idtarea,
                                                                     @RequestBody(required = false) NotaRequest body,
                                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.marcarHecha(idtarea, body == null ? null : body.nota(), userDetails.getUsername()), "tarea");
    }

    @PostMapping("/{idtarea}/deshacer")
    @PreAuthorize("@permisos.tiene('TAREAS_VER')")
    public ResponseEntity<StandardApiResponse<TareaDto>> deshacer(@PathVariable Integer idtarea,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.deshacer(idtarea, userDetails.getUsername()), "tarea");
    }

    // ── Encargado y administrador: toda la tienda ──────────────────────────────

    @GetMapping("/equipo")
    @PreAuthorize("@permisos.tiene('TAREAS_EQUIPO')")
    public ResponseEntity<StandardApiResponse<TareasDiaDto>> equipo(
            @RequestParam Integer codti,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.equipo(codti, fecha, userDetails.getUsername()), "tareas");
    }

    @GetMapping("/cumplimiento")
    @PreAuthorize("@permisos.tiene('TAREAS_EQUIPO')")
    public ResponseEntity<StandardApiResponse<CumplimientoDto>> cumplimiento(
            @RequestParam(required = false) Integer codti,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.cumplimiento(codti, desde, hasta, userDetails.getUsername()), "cumplimiento");
    }

    // ── Rutinas ────────────────────────────────────────────────────────────────

    @GetMapping("/rutinas")
    @PreAuthorize("@permisos.tiene('TAREAS_RUTINAS')")
    public ResponseEntity<StandardApiResponse<List<RutinaDto>>> rutinas(@RequestParam(required = false) Integer codti) {
        return ResponseEntityBuilder.ok(service.rutinas(codti), "rutinas");
    }

    @PostMapping("/rutinas")
    @PreAuthorize("@permisos.tiene('TAREAS_RUTINAS')")
    public ResponseEntity<StandardApiResponse<RutinaDto>> crearRutina(@RequestBody RutinaRequest body,
                                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.created(service.crearRutina(body, userDetails.getUsername()), "rutina");
    }

    @PutMapping("/rutinas/{idrutina}")
    @PreAuthorize("@permisos.tiene('TAREAS_RUTINAS')")
    public ResponseEntity<StandardApiResponse<RutinaDto>> actualizarRutina(@PathVariable Integer idrutina, @RequestBody RutinaRequest body) {
        return ResponseEntityBuilder.updated(service.actualizarRutina(idrutina, body), "rutina");
    }

    @DeleteMapping("/rutinas/{idrutina}")
    @PreAuthorize("@permisos.tiene('TAREAS_RUTINAS')")
    public ResponseEntity<Void> quitarRutina(@PathVariable Integer idrutina) {
        service.quitarRutina(idrutina);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rutinas/{idrutina}/mover")
    @PreAuthorize("@permisos.tiene('TAREAS_RUTINAS')")
    public ResponseEntity<StandardApiResponse<List<RutinaDto>>> mover(@PathVariable Integer idrutina, @RequestParam int direccion) {
        return ResponseEntityBuilder.ok(service.moverRutina(idrutina, direccion), "rutinas");
    }
}
