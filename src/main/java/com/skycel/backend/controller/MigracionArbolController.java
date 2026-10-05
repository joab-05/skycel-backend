package com.skycel.backend.controller;

import com.skycel.backend.domain.dto.response.MigracionArbolResultadoDTO;
import com.skycel.backend.service.MigracionArbolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/migracion-arbol")
@RequiredArgsConstructor
@Tag(name = "Migración a árbol de categorías", description = "Convierte el catálogo anterior al registro por categorías (solo ROOT)")
public class MigracionArbolController {

    private final MigracionArbolService migracionArbolService;

    @Operation(summary = "Plan de migración", description = "Muestra qué haría la migración sin guardar nada.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/plan")
    @PreAuthorize("hasRole('ROOT')")
    public MigracionArbolResultadoDTO plan() {
        return migracionArbolService.planear();
    }

    @Operation(summary = "Aplicar la migración", description = "Requiere confirmar=true. Respaldar la base de datos antes.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/aplicar")
    @PreAuthorize("hasRole('ROOT')")
    public MigracionArbolResultadoDTO aplicar(@RequestParam(defaultValue = "false") boolean confirmar) {
        if (!confirmar) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta operación modifica el catálogo. Respalda la base de datos y vuelve a llamar con confirmar=true.");
        }
        return migracionArbolService.aplicar();
    }
}
