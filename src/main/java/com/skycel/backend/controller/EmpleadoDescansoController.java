package com.skycel.backend.controller;

import com.skycel.backend.service.EmpleadoDescansoService;
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
import java.util.Map;

/** Calendario de descansos tomados por cada empleado (fechas puntuales, no un día fijo semanal). */
@RestController
@RequestMapping("/api/descansos-empleado")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Descansos de empleados", description = "Calendario de fechas de descanso tomadas por cada empleado")
public class EmpleadoDescansoController {

    private final EmpleadoDescansoService service;

    @PostMapping
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> registrar(
            @RequestParam Integer idempleado,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String observaciones,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.registrar(idempleado, fecha, observaciones, userDetails.getUsername()), "descanso");
    }

    @DeleteMapping("/{iddescanso}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer iddescanso, @AuthenticationPrincipal UserDetails userDetails) {
        service.eliminar(iddescanso, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/empleado/{idempleado}")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> porEmpleado(
            @PathVariable Integer idempleado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntityBuilder.ok(service.porEmpleado(idempleado, desde, hasta), "descansos");
    }

    @GetMapping("/tienda/{codti}")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> porTienda(
            @PathVariable Integer codti,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntityBuilder.ok(service.porTienda(codti, desde, hasta), "descansos");
    }
}
