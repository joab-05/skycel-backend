package com.skycel.backend.controller;

import com.skycel.backend.dto.finanzastaller.ComisionTecnicoRequestDto;
import com.skycel.backend.dto.finanzastaller.DescuentoComisionRequestDto;
import com.skycel.backend.dto.finanzastaller.GastoRequestDto;
import com.skycel.backend.service.FinanzasTallerService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/finanzas-taller")
@RequiredArgsConstructor
@Tag(name = "Finanzas del taller", description = "Ganancia mensual de las reparaciones, gastos y comisiones de los técnicos")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("@permisos.tiene('REPORTES_GERENCIA')")
public class FinanzasTallerController {

    private final FinanzasTallerService service;

    @Operation(summary = "El resumen de un mes", description = "Sin año ni mes: el mes actual.")
    @GetMapping
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> mensual(
            @RequestParam(required = false) Integer anio, @RequestParam(required = false) Integer mes,
            @AuthenticationPrincipal UserDetails user) {
        LocalDate hoy = LocalDate.now();
        return ResponseEntityBuilder.ok(service.mensual(anio != null ? anio : hoy.getYear(), mes != null ? mes : hoy.getMonthValue(),
                user.getUsername()), "finanzas del taller");
    }

    @Operation(summary = "Capturar un gasto")
    @PostMapping("/gastos")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> crearGasto(
            @Valid @RequestBody GastoRequestDto dto, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(service.crearGasto(dto, user.getUsername()), "gasto");
    }

    @Operation(summary = "Corregir un gasto")
    @PutMapping("/gastos/{id}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> actualizarGasto(
            @PathVariable Integer id, @Valid @RequestBody GastoRequestDto dto, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(service.actualizarGasto(id, dto, user.getUsername()), "gasto");
    }

    @Operation(summary = "Quitar un gasto")
    @DeleteMapping("/gastos/{id}")
    public ResponseEntity<StandardApiResponse<Object>> eliminarGasto(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        service.eliminarGasto(id, user.getUsername());
        return ResponseEntityBuilder.deleted("gasto");
    }

    @Operation(summary = "El porcentaje de la ganancia neta que gana un técnico")
    @PutMapping("/comisiones/{idusuario}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> fijarPorcentaje(
            @PathVariable Integer idusuario, @Valid @RequestBody ComisionTecnicoRequestDto dto, @AuthenticationPrincipal UserDetails user) {
        service.fijarPorcentaje(idusuario, dto, user.getUsername());
        return ResponseEntityBuilder.updated(Map.of("idusuario", idusuario, "porcentaje", dto.getPorcentaje()), "comisión");
    }

    @Operation(summary = "Descontar algo de la comisión de un técnico en un mes")
    @PostMapping("/descuentos")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> crearDescuento(
            @Valid @RequestBody DescuentoComisionRequestDto dto, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(service.crearDescuento(dto, user.getUsername()), "descuento");
    }

    @Operation(summary = "Quitar un descuento")
    @DeleteMapping("/descuentos/{id}")
    public ResponseEntity<StandardApiResponse<Object>> eliminarDescuento(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        service.eliminarDescuento(id, user.getUsername());
        return ResponseEntityBuilder.deleted("descuento");
    }
}
