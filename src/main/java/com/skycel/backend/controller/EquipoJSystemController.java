package com.skycel.backend.controller;

import com.skycel.backend.service.EquipoJSystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/equipos-jsystem")
@RequiredArgsConstructor
@Tag(name = "Computadoras", description = "Versión de JSystem y estado de la actualización de cada computadora")
@SecurityRequirement(name = "Bearer Authentication")
public class EquipoJSystemController {

    private final EquipoJSystemService service;

    @Operation(summary = "Una computadora reporta su versión y el estado de su actualización")
    @PostMapping("/latido")
    public ResponseEntity<Void> latido(@RequestBody EquipoJSystemService.Latido latido, @AuthenticationPrincipal UserDetails user) {
        service.registrar(latido, user == null ? null : user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Las computadoras con JSystem y si están al día")
    @GetMapping
    @PreAuthorize("@permisos.tiene('NEGOCIO_CONFIG')")
    public ResponseEntity<List<EquipoJSystemService.EquipoDto>> listar() {
        return ResponseEntity.ok(service.listar());
    }
}
