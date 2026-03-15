package com.skycel.backend.controller;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Endpoints para consultar el catálogo de productos y existencias de inventario")
public class ProductoController {

    private final ProductoService productoService;

    @Operation(summary = "Obtener Catálogo Maestro", description = "Retorna todos los productos genéricos del catálogo base")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/master")
    public ResponseEntity<List<ProductoMaster>> getCatalogoMaestro() {
        return ResponseEntity.ok(productoService.obtenerTodosLosProductosMaster());
    }

    @Operation(summary = "Buscar en Catálogo", description = "Busca productos maestros por palabra clave (nombre o nota adicional)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/master/buscar")
    public ResponseEntity<List<ProductoMaster>> buscarProductoMaestro(@RequestParam String query) {
        return ResponseEntity.ok(productoService.buscarProductoMaster(query));
    }

    @Operation(summary = "Obtener Inventario Completo", description = "Retorna los productos físicos (IMEI/Stock) de una tienda específica")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    public ResponseEntity<List<Producto>> getInventarioPorTienda(@PathVariable Integer codti) {
         return ResponseEntity.ok(productoService.obtenerInventarioPorTienda(codti));
    }

    @Operation(summary = "Obtener Stock Disponible", description = "Retorna únicamente los productos con Stock mayor a 0 en una tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/disponibles")
    public ResponseEntity<List<Producto>> getStockDisponible(@PathVariable Integer codti) {
         return ResponseEntity.ok(productoService.obtenerStockDisponiblePorTienda(codti));
    }
}
