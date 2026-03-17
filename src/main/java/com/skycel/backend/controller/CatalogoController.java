package com.skycel.backend.controller;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.request.CatalogoSimpleRequestDTO;
import com.skycel.backend.domain.dto.request.MagnitudRequestDTO;
import com.skycel.backend.domain.dto.request.ProveedorRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.dto.response.CatalogoSimpleResponseDTO;
import com.skycel.backend.domain.dto.response.MagnitudResponseDTO;
import com.skycel.backend.domain.dto.response.ProveedorResponseDTO;
import com.skycel.backend.domain.dto.response.SeccionResponseDTO;
import com.skycel.backend.domain.dto.request.MagnitudRequestDTO;
import com.skycel.backend.domain.dto.request.ProveedorRequestDTO;
import com.skycel.backend.domain.dto.request.SeccionRequestDTO;
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

    @Operation(summary = "Crear color", description = "Crea un nuevo color")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/colores")
    public ResponseEntity<CatalogoSimpleResponseDTO> crearColor(@Valid @RequestBody CatalogoSimpleRequestDTO request) {
        return ResponseEntity.ok(catalogoService.crearColor(request));
    }

    @Operation(summary = "Obtener magnitudes", description = "Retorna la lista de magnitudes activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/magnitudes")
    public ResponseEntity<List<com.skycel.backend.domain.dto.response.MagnitudResponseDTO>> getMagnitudes() {
        return ResponseEntity.ok(catalogoService.obtenerMagnitudesActivas());
    }

    @Operation(summary = "Crear magnitud", description = "Crea una nueva magnitud detallada")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/magnitudes")
    public ResponseEntity<com.skycel.backend.domain.dto.response.MagnitudResponseDTO> crearMagnitud(@Valid @RequestBody com.skycel.backend.domain.dto.request.MagnitudRequestDTO request) {
        return ResponseEntity.ok(catalogoService.crearMagnitud(request));
    }

    @Operation(summary = "Obtener proveedores", description = "Retorna la lista de proveedores activos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/proveedores")
    public ResponseEntity<List<com.skycel.backend.domain.dto.response.ProveedorResponseDTO>> getProveedores() {
        return ResponseEntity.ok(catalogoService.obtenerProveedoresActivos());
    }

    @Operation(summary = "Crear proveedor", description = "Crea un nuevo proveedor con datos fiscales detallados")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/proveedores")
    public ResponseEntity<com.skycel.backend.domain.dto.response.ProveedorResponseDTO> crearProveedor(@Valid @RequestBody com.skycel.backend.domain.dto.request.ProveedorRequestDTO request) {
        return ResponseEntity.ok(catalogoService.crearProveedor(request));
    }

    @Operation(summary = "Obtener secciones", description = "Retorna la lista de secciones activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/secciones")
    public ResponseEntity<List<SeccionResponseDTO>> getSecciones() {
        return ResponseEntity.ok(catalogoService.obtenerSeccionesActivas());
    }

    @Operation(summary = "Crear sección", description = "Crea una nueva sección física en tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/secciones")
    public ResponseEntity<SeccionResponseDTO> crearSeccion(@Valid @RequestBody SeccionRequestDTO request) {
        return ResponseEntity.ok(catalogoService.crearSeccion(request));
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
