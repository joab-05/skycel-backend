package com.skycel.backend.controller;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.dto.configuracion.TiendaDatosRequestDto;
import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;
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
     * PATCH /api/tiendas/{codti}
     * Datos de la sucursal que salen en su ticket (nombre, domicilio y teléfono). Solo ROOT y ADMIN.
     */
    @PatchMapping("/{codti}")
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ResponseEntity<Tienda> actualizarDatos(@PathVariable Integer codti, @Valid @RequestBody TiendaDatosRequestDto request) {
        Tienda t = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Tienda no encontrada: " + codti));
        t.setNombre(request.getNombre().trim());
        t.setUbicacion(request.getUbicacion() == null || request.getUbicacion().isBlank() ? null : request.getUbicacion().trim());
        t.setTelefono(request.getTelefono() == null || request.getTelefono().isBlank() ? null : request.getTelefono().trim());
        return ResponseEntity.ok(tiendaRepository.save(t));
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