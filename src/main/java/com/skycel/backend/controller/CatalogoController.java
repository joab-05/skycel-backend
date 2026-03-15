package com.skycel.backend.controller;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
@Tag(name = "Catálogos", description = "Endpoints de solo lectura para los catálogos del sistema")
public class CatalogoController {

    private final CatalogoService catalogoService;

    @Operation(summary = "Obtener tiendas", description = "Retorna la lista de todas las sucursales")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tiendas")
    public ResponseEntity<List<Tienda>> getTiendas() {
        return ResponseEntity.ok(catalogoService.obtenerTiendas());
    }

    @Operation(summary = "Obtener categorías", description = "Retorna la lista de categorías activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/categorias")
    public ResponseEntity<List<Categoria>> getCategorias() {
        return ResponseEntity.ok(catalogoService.obtenerCategoriasActivas());
    }

    @Operation(summary = "Obtener colores", description = "Retorna la lista de colores activos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/colores")
    public ResponseEntity<List<Color>> getColores() {
        return ResponseEntity.ok(catalogoService.obtenerColoresActivos());
    }

    @Operation(summary = "Obtener magnitudes", description = "Retorna la lista de magnitudes activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/magnitudes")
    public ResponseEntity<List<Magnitud>> getMagnitudes() {
        return ResponseEntity.ok(catalogoService.obtenerMagnitudesActivas());
    }

    @Operation(summary = "Obtener proveedores", description = "Retorna la lista de proveedores activos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/proveedores")
    public ResponseEntity<List<Proveedor>> getProveedores() {
        return ResponseEntity.ok(catalogoService.obtenerProveedoresActivos());
    }

    @Operation(summary = "Obtener secciones", description = "Retorna la lista de secciones activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/secciones")
    public ResponseEntity<List<Seccion>> getSecciones() {
        return ResponseEntity.ok(catalogoService.obtenerSeccionesActivas());
    }
}
