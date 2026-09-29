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

    // ── Saldo (semi-automático) ──────────────────────────────────────────────

    @PostMapping("/{idempleado}/saldo-inicial")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> establecerSaldoInicial(
            @PathVariable Integer idempleado,
            @RequestParam Integer saldoInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCorte,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.establecerSaldoInicial(idempleado, saldoInicial, fechaCorte, userDetails.getUsername()), "saldo");
    }

    @PostMapping("/{idempleado}/ajustes")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> registrarAjuste(
            @PathVariable Integer idempleado,
            @RequestParam Integer dias,
            @RequestParam String motivo,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.registrarAjuste(idempleado, dias, motivo, userDetails.getUsername()), "saldo");
    }

    @GetMapping("/{idempleado}/ajustes")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> ajustesDe(
            @PathVariable Integer idempleado, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.ajustesDe(idempleado, userDetails.getUsername()), "ajustes");
    }

    @GetMapping("/{idempleado}/saldo")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> saldoDe(
            @PathVariable Integer idempleado, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.saldoDe(idempleado, userDetails.getUsername()), "saldo");
    }

    @GetMapping("/reporte")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> reporte(
            @RequestParam(required = false) Integer codti, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.reporte(codti, userDetails.getUsername()), "reporte");
    }
}
