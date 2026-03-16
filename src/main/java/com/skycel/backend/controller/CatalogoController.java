package com.skycel.backend.controller;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.request.CatalogoSimpleRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.dto.response.CatalogoSimpleResponseDTO;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // ==========================================
    // === CATEGORIAS (Jerárquicas)           ===
    // ==========================================

    @Operation(summary = "Obtener categorías", description = "Retorna la lista de categorías activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaResponseDTO>> getCategorias() {
        return ResponseEntity.ok(catalogoService.obtenerCategoriasActivas());
    }

    @Operation(summary = "Crear categoría", description = "Crea una nueva categoría (Opcional: enviar idCategoriaSuperior para crear subcategoría)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/categorias")
    public ResponseEntity<CategoriaResponseDTO> crearCategoria(@Valid @RequestBody CategoriaRequestDTO request) {
        return ResponseEntity.ok(catalogoService.crearCategoria(request));
    }


    // ==========================================
    // === CATÁLOGOS SIMPLES (Color, etc)     ===
    // ==========================================

    @Operation(summary = "Obtener colores", description = "Retorna la lista de colores activos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/colores")
    public ResponseEntity<List<CatalogoSimpleResponseDTO>> getColores() {
        return ResponseEntity.ok(catalogoService.obtenerColoresActivos());
    }

    @Operation(summary = "Obtener magnitudes", description = "Retorna la lista de magnitudes activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/magnitudes")
    public ResponseEntity<List<CatalogoSimpleResponseDTO>> getMagnitudes() {
        return ResponseEntity.ok(catalogoService.obtenerMagnitudesActivas());
    }

    @Operation(summary = "Obtener proveedores", description = "Retorna la lista de proveedores activos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/proveedores")
    public ResponseEntity<List<CatalogoSimpleResponseDTO>> getProveedores() {
        return ResponseEntity.ok(catalogoService.obtenerProveedoresActivos());
    }

    @Operation(summary = "Obtener secciones", description = "Retorna la lista de secciones activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/secciones")
    public ResponseEntity<List<CatalogoSimpleResponseDTO>> getSecciones() {
        return ResponseEntity.ok(catalogoService.obtenerSeccionesActivas());
    }

    // --- Endpoints de Escritura/Borrado Genéricos ---

    @Operation(summary = "Crear registro en catálogo simple", description = "Crea un registro. Tipos válidos: 'color', 'magnitud', 'proveedor', 'seccion'")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{tipo}")
    public ResponseEntity<CatalogoSimpleResponseDTO> crearCatalogoSimple(
            @PathVariable String tipo,
            @Valid @RequestBody CatalogoSimpleRequestDTO request) {
        return ResponseEntity.ok(catalogoService.crearCatalogoSimple(tipo, request));
    }

    @Operation(summary = "Desactivar registro (Soft Delete)", description = "Realiza un borrado lógico dado un catálogo y su ID. Tipos válidos: 'color', 'magnitud', 'proveedor', 'seccion'")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{tipo}/{id}")
    public ResponseEntity<Void> eliminarCatalogoSimple(
            @PathVariable String tipo,
            @PathVariable Short id) {
        catalogoService.eliminarCatalogoSimple(tipo, id);
        return ResponseEntity.noContent().build();
    }
}
