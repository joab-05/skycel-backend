package com.skycel.backend.controller;

import com.skycel.backend.dto.equipo.*;
import com.skycel.backend.service.EquipoService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
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
import java.util.Map;

/** Equipos (celulares y tablets): cada unidad se identifica por su IMEI o serie y apunta directo a su artículo. */
@RestController
@RequestMapping("/api/equipos")
@RequiredArgsConstructor
@Tag(name = "Equipos", description = "Celulares y tablets por IMEI o serie")
public class EquipoController {

    private final EquipoService equipoService;

    @Operation(summary = "Dar de alta equipos (por IMEI o serie) en una sucursal")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("@permisos.tiene('INVENTARIO_AJUSTAR')")
    public ResponseEntity<StandardApiResponse<List<EquipoResponseDto>>> registrar(
            @RequestBody EquipoRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(equipoService.registrar(request, user.getUsername()), "equipos");
    }

    @Operation(summary = "Las unidades de una sucursal (por omisión, disponibles y en garantía)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<List<EquipoResponseDto>>> listar(
            @PathVariable Integer codti, @RequestParam(required = false) List<String> estado,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(equipoService.listar(codti, estado, user.getUsername()), "equipos");
    }

    @Operation(summary = "Los artículos de una sucursal con cuántas unidades hay de cada uno")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/articulos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<List<EquipoArticuloDto>>> articulos(
            @PathVariable Integer codti, @RequestParam(defaultValue = "false") boolean conUnidades,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(equipoService.articulos(codti, conUnidades, user.getUsername()), "equipos");
    }

    @Operation(summary = "Todos los artículos de equipos del sistema (para elegir de cuál se da de alta)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/catalogo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<List<EquipoArticuloDto>>> catalogo() {
        return ResponseEntityBuilder.ok(equipoService.catalogo(), "equipos");
    }

    @Operation(summary = "Buscar equipos disponibles por IMEI/serie o nombre del artículo (punto de venta)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/buscar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<List<EquipoResponseDto>>> buscar(
            @PathVariable Integer codti, @RequestParam("q") String texto, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(equipoService.buscarDisponibles(codti, texto, user.getUsername()), "equipos");
    }

    @Operation(summary = "Un equipo por su IMEI o serie (cualquier estado)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{imei}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<EquipoResponseDto>> porImei(@PathVariable String imei, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(equipoService.porImei(imei, user.getUsername()), "equipo");
    }

    @Operation(summary = "Corregir un equipo: precio, costo, condición, artículo e incluso su IMEI o serie")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/{imei}")
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
    public ResponseEntity<StandardApiResponse<EquipoResponseDto>> editar(
            @PathVariable String imei, @RequestBody EquipoUpdateDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(equipoService.editar(imei, request, user.getUsername()), "equipo");
    }

    @Operation(summary = "Marcar o desmarcar un equipo como rezagado")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{imei}/rezagado")
    @PreAuthorize("@permisos.tiene('INVENTARIO_AJUSTAR')")
    public ResponseEntity<StandardApiResponse<EquipoResponseDto>> rezagado(
            @PathVariable String imei, @RequestParam boolean rezagado, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(equipoService.marcarRezagado(imei, rezagado, user.getUsername()), "equipo");
    }

    @Operation(summary = "Dar de baja un equipo disponible (perdido, dañado, error de captura)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{imei}/baja")
    @PreAuthorize("@permisos.tiene('INVENTARIO_AJUSTAR')")
    public ResponseEntity<StandardApiResponse<EquipoResponseDto>> baja(
            @PathVariable String imei, @RequestBody Map<String, String> cuerpo, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(equipoService.darDeBaja(imei, cuerpo.get("motivo"), user.getUsername()), "equipo");
    }

    @Operation(summary = "Mismo precio (y costo) para todas las unidades disponibles de un artículo en una sucursal")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/articulo/{idprodmaster}/precio")
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> precioDelArticulo(
            @PathVariable Integer idprodmaster, @RequestParam Integer codti, @RequestBody PrecioArticuloDto request,
            @AuthenticationPrincipal UserDetails user) {
        int n = equipoService.precioDelArticulo(idprodmaster, codti, request, user.getUsername());
        return ResponseEntityBuilder.updated(Map.<String, Object>of("unidadesActualizadas", n), "precio");
    }

    @Operation(summary = "Existencias mínimas de un artículo (aviso de bajo stock)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/articulo/{idprodmaster}/stock-minimo")
    @PreAuthorize("@permisos.tiene('INVENTARIO_AJUSTAR')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> stockMinimo(
            @PathVariable Integer idprodmaster, @RequestBody Map<String, Integer> cuerpo) {
        int minimo = cuerpo.getOrDefault("minimo", 0);
        equipoService.stockMinimo(idprodmaster, minimo);
        return ResponseEntityBuilder.updated(Map.<String, Object>of("idprodmaster", idprodmaster, "minimo", minimo), "stock mínimo");
    }
}
