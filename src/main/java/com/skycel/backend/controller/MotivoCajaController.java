package com.skycel.backend.controller;

import com.skycel.backend.dto.caja.MotivoCajaRequestDto;
import com.skycel.backend.dto.caja.MotivoCajaResponseDto;
import com.skycel.backend.service.MotivoCajaService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/motivos-caja")
@RequiredArgsConstructor
@Tag(name = "Motivos de caja", description = "Catálogo de motivos para clasificar los movimientos de efectivo")
public class MotivoCajaController {

    private final MotivoCajaService motivoCajaService;

    @Operation(summary = "Listar motivos (por defecto solo los activos)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<List<MotivoCajaResponseDto>>> listar(
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ResponseEntityBuilder.ok(motivoCajaService.listar(!incluirInactivos), "motivos");
    }

    @Operation(summary = "Agregar un motivo al catálogo")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<MotivoCajaResponseDto>> crear(
            @Valid @RequestBody MotivoCajaRequestDto request) {
        return ResponseEntityBuilder.created(motivoCajaService.crear(request), "motivo");
    }

    @Operation(summary = "Activar o desactivar un motivo")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<MotivoCajaResponseDto>> cambiarActivo(
            @PathVariable Integer id, @RequestParam boolean activo) {
        return ResponseEntityBuilder.updated(motivoCajaService.cambiarActivo(id, activo), "motivo");
    }
}
