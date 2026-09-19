package com.skycel.backend.controller;

import com.skycel.backend.domain.dto.request.*;
import com.skycel.backend.domain.dto.response.*;
import com.skycel.backend.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Gestión de catálogo e inventario")
public class ProductoController {

    private final ProductoService productoService;

    // ── PRODUCTO MASTER ───────────────────────────────────────────────────────

    @Operation(summary = "Catálogo Maestro")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/master")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoMasterResponseDTO>> getCatalogoMaestro() {
        return ResponseEntity.ok(productoService.obtenerTodosMaster());
    }

    @Operation(summary = "Buscar en Catálogo Master")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/master/buscar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoMasterResponseDTO>> buscarMaster(@RequestParam String query) {
        return ResponseEntity.ok(productoService.buscarMaster(query));
    }

    @Operation(summary = "Crear Producto Maestro")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/master")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<ProductoMasterResponseDTO> crearMaster(
            @Valid @RequestBody ProductoMasterRequestDTO request) {
        return ResponseEntity.ok(productoService.crearMaster(request));
    }

    @Operation(summary = "Desactivar Producto Maestro")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/master/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<Void> eliminarMaster(@PathVariable Integer id) {
        productoService.eliminarMaster(id);
        return ResponseEntity.noContent().build();
    }

    // ── PRODUCTO / STOCK ──────────────────────────────────────────────────────

    @Operation(summary = "Inventario por tienda (todos)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoResponseDTO>> getInventarioPorTienda(@PathVariable Integer codti) {
        return ResponseEntity.ok(productoService.obtenerStockPorTienda(codti));
    }

    @Operation(summary = "Stock disponible por tienda (stock > 0)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/disponibles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoResponseDTO>> getStockDisponible(@PathVariable Integer codti) {
        return ResponseEntity.ok(productoService.obtenerStockDisponiblePorTienda(codti));
    }

    @Operation(summary = "Productos en bajo stock (stock <= stock mínimo) de una tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/bajo-stock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoResponseDTO>> getBajoStock(@PathVariable Integer codti) {
        return ResponseEntity.ok(productoService.obtenerBajoStock(codti));
    }

    @Operation(summary = "Accesorios con stock compatibles con un modelo (incluye los Universal)",
            description = "Ejemplo: /api/productos/tienda/2/compatibles?con=iPhone 13")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/compatibles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoResponseDTO>> getAccesoriosCompatibles(
            @PathVariable Integer codti, @RequestParam String con) {
        return ResponseEntity.ok(productoService.obtenerAccesoriosCompatibles(codti, con));
    }

    @Operation(summary = "Buscar producto por IMEI")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/imei/{imei}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductoResponseDTO> getProductoPorImei(@PathVariable String imei) {
        return ResponseEntity.ok(productoService.obtenerProductoPorImei(imei));
    }

    @Operation(summary = "Registrar producto en tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<ProductoResponseDTO> crearProducto(
            @Valid @RequestBody ProductoRequestDTO request) {
        return ResponseEntity.ok(productoService.crearProducto(request));
    }

    @Operation(summary = "Actualizar precios y datos de un producto")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/{codpro}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<ProductoResponseDTO> actualizarProducto(
            @PathVariable String codpro,
            @RequestBody ProductoUpdateDTO request) {
        return ResponseEntity.ok(productoService.actualizar(codpro, request));
    }

    @Operation(summary = "Ajustar stock — ENTRADA / SALIDA / AJUSTE")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{codpro}/stock")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<ProductoResponseDTO> ajustarStock(
            @PathVariable String codpro,
            @RequestBody StockAjusteDTO request) {
        return ResponseEntity.ok(productoService.ajustarStock(codpro, request));
    }

    @Operation(summary = "Fijar (o quitar) el precio propio de un IMEI específico, distinto al del modelo")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/imei/{imei}/precio")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<com.skycel.backend.domain.dto.response.ImeiInfoDTO> actualizarPrecioImei(
            @PathVariable String imei,
            @RequestBody com.skycel.backend.domain.dto.request.ImeiPrecioUpdateDTO request) {
        return ResponseEntity.ok(productoService.actualizarPrecioImei(imei, request));
    }

    @Operation(summary = "Corregir la condición (NUEVO/USADO/REACONDICIONADO) o el costo de una unidad disponible")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/imei/{imei}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<ImeiInfoDTO> actualizarImei(
            @PathVariable String imei, @Valid @RequestBody ImeiUpdateDTO request) {
        return ResponseEntity.ok(productoService.actualizarImei(imei, request));
    }

    @Operation(summary = "Actualizar compatibilidad, tiempo estimado o garantía de un producto maestro")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/master/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<ProductoMasterResponseDTO> actualizarMaster(
            @PathVariable Integer id, @Valid @RequestBody ProductoMasterUpdateDTO request) {
        return ResponseEntity.ok(productoService.actualizarMaster(id, request));
    }

    @Operation(summary = "Desactivar producto (soft delete)")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{codpro}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<Void> eliminarProducto(@PathVariable String codpro) {
        productoService.eliminarProducto(codpro);
        return ResponseEntity.noContent().build();
    }
}
