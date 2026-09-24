package com.skycel.backend.controller;

import com.skycel.backend.domain.dto.request.*;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.dto.response.CatalogoSimpleResponseDTO;
import com.skycel.backend.domain.dto.response.MagnitudResponseDTO;
import com.skycel.backend.domain.dto.response.ProveedorResponseDTO;
import com.skycel.backend.domain.dto.response.SeccionResponseDTO;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.service.CatalogoService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
@Tag(name = "Catálogos", description = "Endpoints de lectura, inserción, actualización y eliminación para los catálogos del sistema")
public class CatalogoController {

    private final CatalogoService catalogoService;

    @Operation(summary = "Obtener tiendas", description = "Retorna la lista de todas las sucursales activas ")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tiendas")
    public ResponseEntity<StandardApiResponse<List<Tienda>>> getTiendas() {
        return ResponseEntityBuilder.ok(catalogoService.obtenerTiendas(),"tiendas");
    }

    @Operation(summary = "Listar todos los proveedores", description = "Incluye activos e inactivos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/proveedores/todos")
    public ResponseEntity<StandardApiResponse<List<ProveedorResponseDTO>>> getTodosProveedores() {
        return ResponseEntityBuilder.ok(catalogoService.obtenerTodosProveedores(), "proveedores");
    }

    @Operation(summary = "Actualizar proveedor")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/proveedores/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<ProveedorResponseDTO>> actualizarProveedor(
            @PathVariable Short id,
            @Valid @RequestBody ProveedorRequestDTO request) {
        return ResponseEntityBuilder.updated(catalogoService.actualizarProveedor(id, request), "proveedor");
    }

    // ==========================================
    // === CATEGORIAS (Jerárquicas)           ===
    // ==========================================

    @Operation(summary = "Obtener categorías", description = "Retorna la lista de categorías activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/categorias")
    public ResponseEntity<StandardApiResponse<List<CategoriaResponseDTO>>> getCategorias() {
        return ResponseEntityBuilder.ok(catalogoService.obtenerCategoriasActivas(), "categorias");
    }

    @Operation(summary = "Crear categoría", description = "Crea una nueva categoría (Opcional: enviar idCategoriaSuperior para crear subcategoría)")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Categoria creada exitosamente"), @ApiResponse(responseCode = "400", description = "Datos inválidos"), @ApiResponse(responseCode = "409", description = "Ya existe categoria con ese nombre"), @ApiResponse(responseCode = "401", description = "No autenticado")})
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/categorias")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<CategoriaResponseDTO>> crearCategoria(@Valid @RequestBody CategoriaRequestDTO request) {
        return ResponseEntityBuilder.created(catalogoService.crearCategoria(request),"categoria");
    }

    @Operation(summary = "Actualizar categoria", description = "Actualiza una categoria existente por su ID. El nombre debe ser único.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Categoria actualizada exitosamente"), @ApiResponse(responseCode = "400", description = "Datos inválidos"), @ApiResponse(responseCode = "404", description = "Categoria no encontrada"), @ApiResponse(responseCode = "409", description = "Ya existe otra categoria con ese nombre"), @ApiResponse(responseCode = "401", description = "No autenticado")})
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/categorias/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<CategoriaResponseDTO>> actualizarCategoria(@PathVariable Short id, @Valid @RequestBody CategoriaRequestDTO request) {
        return ResponseEntityBuilder.updated(catalogoService.actualizarCategoria(id, request), "categoria");
    }

    // ==========================================
    // === CATÁLOGOS SIMPLES (Color, etc)     ===
    // ==========================================

    @Operation(summary = "Obtener colores", description = "Retorna la lista de colores activos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/colores")
    public ResponseEntity<StandardApiResponse<List<CatalogoSimpleResponseDTO>>> getColores() {
        return ResponseEntityBuilder.ok(catalogoService.obtenerColoresActivos(),"colores");
    }

    @Operation(summary = "Crear color", description = "Crea un nuevo color")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/colores")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<CatalogoSimpleResponseDTO>> crearColor(@Valid @RequestBody CatalogoSimpleRequestDTO request) {
        return ResponseEntityBuilder.created(catalogoService.crearColor(request),"color");
    }

    @Operation(summary = "Obtener magnitudes", description = "Retorna la lista de magnitudes activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/magnitudes")
    public ResponseEntity<StandardApiResponse<List<MagnitudResponseDTO>>> getMagnitudes() {
        return ResponseEntityBuilder.ok(catalogoService.obtenerMagnitudesActivas(),"magnitudes");
    }

    @Operation(summary = "Crear magnitud", description = "Crea una nueva magnitud. El nombre debe ser único.")
    @ApiResponses({ @ApiResponse(responseCode = "201", description = "Magnitud creada exitosamente"), @ApiResponse(responseCode = "400", description = "Datos inválidos"), @ApiResponse(responseCode = "409", description = "Ya existe magnitud con ese nombre"), @ApiResponse(responseCode = "401", description = "No autenticado")})
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/magnitudes")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<MagnitudResponseDTO>> crearMagnitud(@Valid @RequestBody com.skycel.backend.domain.dto.request.MagnitudRequestDTO request) {
        return ResponseEntityBuilder.created(catalogoService.crearMagnitud(request),"magnitud");
    }
    @Operation(summary = "Actualizar magnitud", description = "Actualiza una magnitud existente por su ID. El nombre debe ser único.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Magnitud actualizada exitosamente")})
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/magnitudes/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<MagnitudResponseDTO>> actualizarMagnitud(@PathVariable Short id, @Valid @RequestBody MagnitudRequestDTO request) {
        return ResponseEntityBuilder.updated(catalogoService.actualizarMagnitud(id, request),"magnitud");
    }

    @Operation(summary = "Obtener proveedores", description = "Retorna la lista de proveedores activos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/proveedores")
    public ResponseEntity<StandardApiResponse<List<ProveedorResponseDTO>>> getProveedores() {
        return ResponseEntityBuilder.ok(catalogoService.obtenerProveedoresActivos(),"proveedores");
    }

    @Operation(summary = "Crear proveedor", description = "Crea un nuevo proveedor con datos fiscales detallados")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/proveedores")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<ProveedorResponseDTO>> crearProveedor(@Valid @RequestBody com.skycel.backend.domain.dto.request.ProveedorRequestDTO request) {
        return ResponseEntityBuilder.created(catalogoService.crearProveedor(request),"proveedor");
    }

    @Operation(summary = "Obtener secciones", description = "Retorna la lista de secciones activas")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/secciones")
    public ResponseEntity<StandardApiResponse<List<SeccionResponseDTO>>> getSecciones() {
        return ResponseEntityBuilder.ok(catalogoService.obtenerSeccionesActivas(),"secciones");
    }

    @Operation(summary = "Crear sección", description = "Crea una nueva sección física en tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/secciones")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<SeccionResponseDTO>> crearSeccion(@Valid @RequestBody SeccionRequestDTO request) {
        return ResponseEntityBuilder.created(catalogoService.crearSeccion(request), "seccion");
    }

    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{tipo}/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    @Operation(summary = "Desactivar registros (Soft Delete) ", description = "Realiza un borrado lógico dado un catálogo y su ID. Tipos válidos: 'color', 'magnitud', 'proveedor', 'seccion'")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Eliminacion exitosa"), @ApiResponse(responseCode = "404", description = "No encontrada"), @ApiResponse(responseCode = "401", description = "No autenticado")})
    public ResponseEntity<StandardApiResponse<Void>> eliminarCatalogoSimple(@PathVariable String tipo, @PathVariable Short id) {
        catalogoService.eliminarCatalogoSimple(tipo, id);
        return ResponseEntityBuilder.deleted(tipo);
    }
}
