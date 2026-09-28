package com.skycel.backend.controller;

import com.skycel.backend.service.ReporteService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * Reportes gerenciales: comparativo de ventas y utilidad entre sucursales, artículos más vendidos y valor
 * del inventario. Un encargado ve solo su propia sucursal; ROOT/ADMIN pueden comparar todas a la vez.
 */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Reportes gerenciales de ventas, utilidad e inventario")
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping("/gerencial")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> gerencial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer codti,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(reporteService.gerencial(desde, hasta, codti, userDetails.getUsername()), "reporte");
    }

    @GetMapping("/equipos-rezagados")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> equiposRezagados(
            @RequestParam(required = false) Integer dias,
            @RequestParam(required = false) Integer codti,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(reporteService.equiposRezagados(dias, codti, userDetails.getUsername()), "reporte");
    }

    @GetMapping("/caja-consolidada")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> cajaConsolidada(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(reporteService.cajaConsolidada(userDetails.getUsername()), "reporte");
    }
}
