package com.skycel.backend.controller;

import com.skycel.backend.domain.dto.request.ProductoMasterRequestDTO;
import com.skycel.backend.domain.dto.request.ProductoRequestDTO;
import com.skycel.backend.domain.dto.response.ProductoMasterResponseDTO;
import com.skycel.backend.domain.dto.response.ProductoResponseDTO;
import com.skycel.backend.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Endpoints para gestionar el catálogo y existencias de inventario")
public class ProductoController {

    private final ProductoService productoService;

    // ==========================================
    // === PRODUCTO MASTER                    ===
    // ==========================================

    @Operation(summary = "Obtener Catálogo Maestro", description = "Retorna todos los productos genéricos activos")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/master")
    public ResponseEntity<List<ProductoMasterResponseDTO>> getCatalogoMaestro() {
        return ResponseEntity.ok(productoService.obtenerTodosMaster());
    }

    @Operation(summary = "Crear Producto Maestro", description = "Registra un nuevo producto base en el catálogo")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/master")
    public ResponseEntity<ProductoMasterResponseDTO> crearMaster(@Valid @RequestBody ProductoMasterRequestDTO request) {
        return ResponseEntity.ok(productoService.crearMaster(request));
    }

    @Operation(summary = "Buscar en Catálogo Master", description = "Busca productos maestros por palabra clave")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/master/buscar")
    public ResponseEntity<List<ProductoMasterResponseDTO>> buscarMaster(@RequestParam String query) {
        return ResponseEntity.ok(productoService.buscarMaster(query));
    }

    @Operation(summary = "Borrado Lógico Master", description = "Desactiva un producto maestro")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/master/{id}")
    public ResponseEntity<Void> eliminarMaster(@PathVariable Integer id) {
        productoService.eliminarMaster(id);
        return ResponseEntity.noContent().build();
    }


    // ==========================================
    // === PRODUCTO (Stock/Inventario)         ===
    // ==========================================

    @Operation(summary = "Obtener Inventario por Tienda", description = "Retorna existencias de una sucursal")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    public ResponseEntity<List<ProductoResponseDTO>> getInventarioPorTienda(@PathVariable Integer codti) {
         return ResponseEntity.ok(productoService.obtenerStockPorTienda(codti));
    }

    @Operation(summary = "Obtener Stock Disponible", description = "Retorna productos con existencia > 0")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/disponibles")
    public ResponseEntity<List<ProductoResponseDTO>> getStockDisponible(@PathVariable Integer codti) {
         return ResponseEntity.ok(productoService.obtenerStockDisponiblePorTienda(codti));
    }

    @Operation(summary = "Crear SKU/Producto en Tienda", description = "Registra una variante de producto con stock inicial")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crearProducto(@Valid @RequestBody ProductoRequestDTO request) {
        return ResponseEntity.ok(productoService.crearProducto(request));
    }

    @Operation(summary = "Borrado Lógico de Producto", description = "Desactiva un SKU específico por su codpro")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{codpro}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable String codpro) {
        productoService.eliminarProducto(codpro);
        return ResponseEntity.noContent().build();
    }
}
