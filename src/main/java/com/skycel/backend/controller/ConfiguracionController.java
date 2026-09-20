package com.skycel.backend.controller;

import com.skycel.backend.dto.configuracion.ConfiguracionNegocioDto;
import com.skycel.backend.service.ConfiguracionNegocioService;
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

@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
@Tag(name = "Configuración", description = "Datos del negocio compartidos por todas las tiendas")
public class ConfiguracionController {

    private final ConfiguracionNegocioService configuracionService;

    @Operation(summary = "Datos del negocio",
            description = "Nombre comercial, razón social, RFC, pie y leyenda del ticket y plazo de devoluciones. Los lee cualquier usuario " +
                    "(los tickets los necesitan).")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/negocio")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<ConfiguracionNegocioDto>> obtener() {
        return ResponseEntityBuilder.ok(configuracionService.obtener(), "datos del negocio");
    }

    @Operation(summary = "Guardar los datos del negocio")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/negocio")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<ConfiguracionNegocioDto>> actualizar(
            @Valid @RequestBody ConfiguracionNegocioDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(configuracionService.actualizar(request, user.getUsername()), "datos del negocio");
    }
}
