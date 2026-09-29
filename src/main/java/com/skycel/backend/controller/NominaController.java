package com.skycel.backend.controller;

import com.skycel.backend.service.NominaService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Nómina quincenal: períodos, líneas por empleado con percepciones/deducciones, y pago. Solo ROOT/ADMIN. */
@RestController
@RequestMapping("/api/nomina")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROOT','ADMIN')")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Nómina", description = "Períodos de nómina, percepciones, deducciones y pago")
public class NominaController {

    private final NominaService service;

    @PostMapping("/periodos")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> abrirPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPago,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.abrirPeriodo(fechaInicio, fechaFin, fechaPago, userDetails.getUsername()), "periodo");
    }

    @GetMapping("/periodos/activo")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> periodoActivo() {
        return ResponseEntityBuilder.ok(service.periodoActivo(), "periodo");
    }

    @GetMapping("/periodos")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> periodos() {
        return ResponseEntityBuilder.ok(service.periodos(), "periodos");
    }

    @PostMapping("/periodos/{idperiodo}/cerrar")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> cerrarPeriodo(@PathVariable Integer idperiodo) {
        return ResponseEntityBuilder.ok(service.cerrarPeriodo(idperiodo), "periodo");
    }

    @GetMapping("/periodos/{idperiodo}/detalles")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> porPeriodo(@PathVariable Integer idperiodo) {
        return ResponseEntityBuilder.ok(service.porPeriodo(idperiodo), "resultado");
    }

    @GetMapping("/detalles/{iddetalle}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> detalle(@PathVariable Integer iddetalle) {
        return ResponseEntityBuilder.ok(service.detalle(iddetalle), "detalle");
    }

    @PostMapping("/detalles/{iddetalle}/percepciones")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> agregarPercepcion(
            @PathVariable Integer iddetalle, @RequestParam String concepto, @RequestParam BigDecimal monto) {
        return ResponseEntityBuilder.ok(service.agregarPercepcion(iddetalle, concepto, monto), "detalle");
    }

    @DeleteMapping("/percepciones/{idpercepcion}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> eliminarPercepcion(@PathVariable Integer idpercepcion) {
        return ResponseEntityBuilder.ok(service.eliminarPercepcion(idpercepcion), "detalle");
    }

    @PostMapping("/detalles/{iddetalle}/deducciones")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> agregarDeduccion(
            @PathVariable Integer iddetalle, @RequestParam String concepto, @RequestParam BigDecimal monto) {
        return ResponseEntityBuilder.ok(service.agregarDeduccion(iddetalle, concepto, monto), "detalle");
    }

    @DeleteMapping("/deducciones/{iddeduccion}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> eliminarDeduccion(@PathVariable Integer iddeduccion) {
        return ResponseEntityBuilder.ok(service.eliminarDeduccion(iddeduccion), "detalle");
    }

    @PostMapping("/detalles/{iddetalle}/pagar")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> pagar(
            @PathVariable Integer iddetalle, @RequestParam Integer idCaja,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.pagar(iddetalle, idCaja, userDetails.getUsername()), "detalle");
    }
}
