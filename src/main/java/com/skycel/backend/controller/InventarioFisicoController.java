package com.skycel.backend.controller;

import com.skycel.backend.service.InventarioFisicoService;
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

import java.util.List;
import java.util.Map;

/**
 * Inventario físico por secciones: Aperturar → Inventariar (escanear) → Catalogar diferencias → Finalizar.
 * ROOT/ADMIN operan cualquier sucursal; ENCARGADO_TIENDA solo la suya (ver el servicio).
 */
@RestController
@RequestMapping("/api/inventario-fisico")
@RequiredArgsConstructor
@Tag(name = "Inventario físico", description = "Conteo físico de inventario por sucursal, con catalogación de diferencias")
public class InventarioFisicoController {

    private final InventarioFisicoService service;

    @PostMapping("/aperturar")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> aperturar(
            @RequestParam Integer codti,
            @RequestParam(required = false) Integer idusuarioEncargado,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.abrir(codti, idusuarioEncargado, userDetails.getUsername()), "auditoria");
    }

    @PostMapping("/{idinventario}/escanear")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> escanear(
            @PathVariable Integer idinventario,
            @RequestParam String codpro,
            @RequestParam(required = false) Integer cantidad,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.escanear(idinventario, codpro, cantidad, userDetails.getUsername()), "detalle");
    }

    @GetMapping("/{idinventario}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> detalle(
            @PathVariable Integer idinventario,
            @RequestParam(required = false, defaultValue = "false") boolean soloDiferencias,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.detalle(idinventario, soloDiferencias, userDetails.getUsername()), "auditoria");
    }

    @PostMapping("/{idinventario}/cerrar-conteo")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> cerrarConteo(
            @PathVariable Integer idinventario,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.cerrarConteo(idinventario, userDetails.getUsername()), "auditoria");
    }

    @PostMapping("/{idinventario}/detalle/{iddetalleInv}/resolver")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> resolverDetalle(
            @PathVariable Integer idinventario,
            @PathVariable Integer iddetalleInv,
            @RequestParam String accion,
            @RequestParam(required = false) String motivo,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.resolverDetalle(idinventario, iddetalleInv, accion, motivo, userDetails.getUsername()), "detalle");
    }

    @PostMapping("/{idinventario}/finalizar")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> finalizar(
            @PathVariable Integer idinventario,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.finalizar(idinventario, userDetails.getUsername()), "auditoria");
    }

    @GetMapping("/tienda/{codti}/activa")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> activa(
            @PathVariable Integer codti,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.activa(codti, userDetails.getUsername()), "auditoria");
    }

    @GetMapping("/tienda/{codti}/historial")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> historial(
            @PathVariable Integer codti,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.historial(codti, userDetails.getUsername()), "auditorias");
    }
}
