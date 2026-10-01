package com.skycel.backend.controller;

import com.skycel.backend.dto.comision.ComisionLineaDto;
import com.skycel.backend.dto.comision.ConfigComisionDto;
import com.skycel.backend.service.ComisionService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Comisiones por venta: la mensual de encargado de tienda y la de PayJoy por equipo vendido. Solo ROOT/ADMIN:
 *  es dinero que se decide pagar, no se delega a encargados. */
@RestController
@RequestMapping("/api/comisiones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROOT','ADMIN')")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Comisiones", description = "Comisión mensual de encargado y comisión por venta PayJoy")
public class ComisionController {

    private final ComisionService service;

    @GetMapping("/config")
    public ResponseEntity<StandardApiResponse<ConfigComisionDto>> configuracion() {
        return ResponseEntityBuilder.ok(service.configuracion(), "config");
    }

    @PutMapping("/config")
    public ResponseEntity<StandardApiResponse<ConfigComisionDto>> actualizarConfiguracion(
            @Valid @RequestBody ConfigComisionDto dto, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.actualizarConfiguracion(dto, userDetails.getUsername()), "config");
    }

    @GetMapping("/encargados")
    public ResponseEntity<StandardApiResponse<List<ComisionLineaDto>>> encargados(
            @RequestParam int anio, @RequestParam int mes) {
        return ResponseEntityBuilder.ok(service.calcularEncargados(anio, mes), "comisiones");
    }

    @PostMapping("/encargados/{idempleado}/pagar")
    public ResponseEntity<StandardApiResponse<ComisionLineaDto>> pagarEncargado(
            @PathVariable Integer idempleado, @RequestParam int anio, @RequestParam int mes,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.marcarPagadaEncargado(idempleado, anio, mes, userDetails.getUsername()), "comision");
    }

    @GetMapping("/payjoy")
    public ResponseEntity<StandardApiResponse<List<ComisionLineaDto>>> payjoy(
            @RequestParam int anio, @RequestParam int mes) {
        return ResponseEntityBuilder.ok(service.calcularPayjoy(anio, mes), "comisiones");
    }

    @PostMapping("/payjoy/{idempleado}/pagar")
    public ResponseEntity<StandardApiResponse<ComisionLineaDto>> pagarPayjoy(
            @PathVariable Integer idempleado, @RequestParam int anio, @RequestParam int mes,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.marcarPagadaPayjoy(idempleado, anio, mes, userDetails.getUsername()), "comision");
    }
}
