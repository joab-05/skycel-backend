package com.skycel.backend.controller;

import com.skycel.backend.service.PrecioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/precios")
@RequiredArgsConstructor
@Tag(name = "Precios", description = "Vista de precios para administradores: cambiar el precio de varios artículos a la vez")
@SecurityRequirement(name = "Bearer Authentication")
public class PrecioController {

    private final PrecioService service;

    @Operation(summary = "Los artículos con su precio actual (el más bajo y el más alto entre sucursales)")
    @GetMapping
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
    public ResponseEntity<List<PrecioService.Articulo>> listar(@RequestParam(required = false) String texto,
                                                               @RequestParam(required = false) String tipo) {
        return ResponseEntity.ok(service.listar(texto, tipo));
    }

    @Operation(summary = "Cambia el precio de varios artículos en todas las sucursales y la bodega")
    @PutMapping
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
    public ResponseEntity<PrecioService.Resultado> aplicar(@RequestBody List<PrecioService.Cambio> cambios,
                                                            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.aplicar(cambios, user.getUsername()));
    }
}
