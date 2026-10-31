package com.skycel.backend.controller;

import com.skycel.backend.service.RespaldoService;
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

/** Respaldo de la base de datos: ver cómo van y hacer uno en el momento (solo root). */
@RestController
@RequestMapping("/api/respaldos")
@RequiredArgsConstructor
@Tag(name = "Respaldos", description = "Respaldo diario de la base de datos")
public class RespaldoController {

    private final RespaldoService service;

    @Operation(summary = "Estado del respaldo automático y últimas copias")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize("hasRole('ROOT')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> estado() {
        return ResponseEntityBuilder.ok(service.estado(), "respaldos");
    }

    @Operation(summary = "Hacer un respaldo ahora")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/ejecutar")
    @PreAuthorize("hasRole('ROOT')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> ejecutar() {
        return ResponseEntityBuilder.ok(service.ejecutar(), "respaldo");
    }
}
