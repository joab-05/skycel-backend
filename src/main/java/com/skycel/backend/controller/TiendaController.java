package com.skycel.backend.controller;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.repository.TiendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de consulta de tiendas/sucursales.
 * Base: /api/tiendas
 */
@RestController
@RequestMapping("/api/tiendas")
@RequiredArgsConstructor
public class TiendaController {

    private final TiendaRepository tiendaRepository;

    /**
     * GET /api/tiendas
     * Lista todas las tiendas activas. Accesible para cualquier usuario autenticado.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Tienda>> listarActivas() {
        List<Tienda> tiendas = tiendaRepository.findAll()
                .stream()
                .filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .toList();
        return ResponseEntity.ok(tiendas);
    }

    /**
     * GET /api/tiendas/todas
     * Incluye inactivas. Solo ROOT y ADMIN.
     */
    @GetMapping("/todas")
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ResponseEntity<List<Tienda>> listarTodas() {
        return ResponseEntity.ok(tiendaRepository.findAll());
    }
}