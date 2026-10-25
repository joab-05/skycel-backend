package com.skycel.backend.controller;

import com.skycel.backend.dto.descuento.SolicitudDescuentoRequestDto;
import com.skycel.backend.service.SolicitudDescuentoService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Solicitudes de descuento: quien vende la pide desde el punto de venta y un administrador la aprueba o la rechaza. Pedirla la puede
 * hacer quien vende; resolverla, quien define descuentos.
 */
@RestController
@RequestMapping("/api/descuentos/solicitudes")
@RequiredArgsConstructor
@Tag(name = "Descuentos", description = "Solicitudes de descuento al administrador")
public class SolicitudDescuentoController {

    private final SolicitudDescuentoService service;

    @PostMapping
    @PreAuthorize("@permisos.tiene('VENTA_CREAR')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> crear(
            @RequestBody SolicitudDescuentoRequestDto dto, @AuthenticationPrincipal UserDetails u) {
        return ResponseEntityBuilder.created(service.crear(dto, u.getUsername()), "solicitud");
    }

    @GetMapping("/pendientes")
    @PreAuthorize("@permisos.tiene('DESCUENTOS')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> pendientes() {
        return ResponseEntityBuilder.ok(service.pendientes(), "solicitudes");
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permisos.tiene('VENTA_CREAR')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> obtener(@PathVariable Integer id, @AuthenticationPrincipal UserDetails u) {
        return ResponseEntityBuilder.ok(service.obtener(id, u.getUsername()), "solicitud");
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("@permisos.tiene('DESCUENTOS')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> aprobar(
            @PathVariable Integer id, @RequestParam(required = false) BigDecimal descuentoPorPieza, @AuthenticationPrincipal UserDetails u) {
        return ResponseEntityBuilder.updated(service.aprobar(id, descuentoPorPieza, u.getUsername()), "solicitud");
    }

    @PatchMapping("/{id}/rechazar")
    @PreAuthorize("@permisos.tiene('DESCUENTOS')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> rechazar(
            @PathVariable Integer id, @RequestParam(required = false) String motivo, @AuthenticationPrincipal UserDetails u) {
        return ResponseEntityBuilder.updated(service.rechazar(id, motivo, u.getUsername()), "solicitud");
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("@permisos.tiene('VENTA_CREAR')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> cancelar(@PathVariable Integer id, @AuthenticationPrincipal UserDetails u) {
        return ResponseEntityBuilder.updated(service.cancelar(id, u.getUsername()), "solicitud");
    }
}
