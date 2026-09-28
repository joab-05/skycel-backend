package com.skycel.backend.controller;

import com.skycel.backend.dto.descuento.DescuentoReglaRequestDto;
import com.skycel.backend.service.DescuentoService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Reglas de descuento automático del punto de venta. Solo ROOT/ADMIN las administran; el cálculo en sí
 * (qué descuento aplica a un producto) lo usan ProductoService (para mostrarlo) y VentaService (para
 * cobrarlo) sin pasar por aquí.
 */
@RestController
@RequestMapping("/api/descuentos")
@RequiredArgsConstructor
@Tag(name = "Descuentos", description = "Reglas de descuento automático del punto de venta")
public class DescuentoController {

    private final DescuentoService descuentoService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> listar() {
        return ResponseEntityBuilder.ok(descuentoService.listar(), "descuentos");
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> crear(@RequestBody DescuentoReglaRequestDto dto) {
        return ResponseEntityBuilder.created(descuentoService.crear(dto), "descuento");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> actualizar(
            @PathVariable Integer id, @RequestBody DescuentoReglaRequestDto dto) {
        return ResponseEntityBuilder.updated(descuentoService.actualizar(id, dto), "descuento");
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> cambiarActivo(
            @PathVariable Integer id, @RequestParam boolean activo) {
        return ResponseEntityBuilder.updated(descuentoService.cambiarActivo(id, activo), "descuento");
    }
}
