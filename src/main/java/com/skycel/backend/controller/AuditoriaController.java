package com.skycel.backend.controller;

import com.skycel.backend.service.AuditoriaService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Historial de cambios (quién, cuándo y qué) de productos, artículos maestros, usuarios y tiendas.
 * Solo ROOT/ADMIN: es información de control, del mismo nivel que ver costos o cambiar precios.
 */
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Historial de cambios de registros auditados")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping("/{entidad}/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> historial(
            @PathVariable String entidad, @PathVariable Integer id) {
        return ResponseEntityBuilder.ok(auditoriaService.historial(entidad, id), "historial");
    }
}
