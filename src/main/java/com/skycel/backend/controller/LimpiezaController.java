package com.skycel.backend.controller;

import com.skycel.backend.service.LimpiezaService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Limpieza automática de datos viejos: ver los plazos y la última corrida, o hacerla en el momento (solo root). */
@RestController
@RequestMapping("/api/limpieza")
@RequiredArgsConstructor
@Tag(name = "Limpieza", description = "Limpieza automática de sesiones, avisos leídos e historial de cambios viejos")
public class LimpiezaController {

    private final LimpiezaService service;

    @Operation(summary = "Plazos y última limpieza")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize("hasRole('ROOT')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> estado() {
        return ResponseEntityBuilder.ok(service.estado(), "limpieza");
    }

    @Operation(summary = "Limpiar ahora")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/ejecutar")
    @PreAuthorize("hasRole('ROOT')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> ejecutar() {
        return ResponseEntityBuilder.ok(service.ejecutar(), "limpieza");
    }
}
