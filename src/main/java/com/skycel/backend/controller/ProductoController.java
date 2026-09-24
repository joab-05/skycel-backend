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
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Gestión de catálogo e inventario")
public class ProductoController {

    private final ProductoService productoService;
    private final com.skycel.backend.service.MovimientoInventarioService movimientoInventarioService;

    /** Quién puede ver cuánto costó la mercancía: administradores y encargados. Vendedores y técnicos, no. */
    private static boolean veCostos(org.springframework.security.core.userdetails.UserDetails u) {
        return u != null && u.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ROOT") || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_ENCARGADO_TIENDA"));
    }
    private static ProductoResponseDTO sinCostos(ProductoResponseDTO d, org.springframework.security.core.userdetails.UserDetails u) {
        if (veCostos(u)) return d;
        d.setPreciopro(null);
        if (d.getImeisDisponibles() != null) {
            d.getImeisDisponibles().forEach(i -> { i.setCostoUnitario(null); i.setTieneCostoPropio(null); });
        }
        return d;
    }
    private static List<ProductoResponseDTO> sinCostosSiAplica(List<ProductoResponseDTO> lista, org.springframework.security.core.userdetails.UserDetails u) {
        if (!veCostos(u)) lista.forEach(d -> sinCostos(d, u));
        return lista;
    }

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
    public ResponseEntity<List<ProductoResponseDTO>> getInventarioPorTienda(@PathVariable Integer codti,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails user) {
        return ResponseEntity.ok(sinCostosSiAplica(productoService.obtenerStockPorTienda(codti), user));
    }

    @Operation(summary = "Stock disponible por tienda (stock > 0)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/disponibles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoResponseDTO>> getStockDisponible(@PathVariable Integer codti,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails user) {
        return ResponseEntity.ok(sinCostosSiAplica(productoService.obtenerStockDisponiblePorTienda(codti), user));
    }

    @Operation(summary = "Historial de cambios de stock de una tienda",
            description = "Entradas, salidas, ajustes, ventas y traspasos, con el stock antes y después, el motivo y quién lo hizo. " +
                    "Por defecto los últimos 30 días; filtro opcional por producto. Un encargado solo consulta su tienda.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/movimientos")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<List<com.skycel.backend.dto.producto.MovimientoInventarioResponseDto>> movimientos(
            @PathVariable Integer codti,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime hasta,
            @RequestParam(required = false) String codpro,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails user) {
        return ResponseEntity.ok(movimientoInventarioService.listar(codti, desde, hasta, codpro, user.getUsername()));
    }

    @Operation(summary = "Productos en bajo stock (stock <= stock mínimo) de una tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/bajo-stock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoResponseDTO>> getBajoStock(@PathVariable Integer codti,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails user) {
        return ResponseEntity.ok(sinCostosSiAplica(productoService.obtenerBajoStock(codti), user));
    }

    @Operation(summary = "Accesorios con stock compatibles con un modelo (incluye los Universal)",
            description = "Ejemplo: /api/productos/tienda/2/compatibles?con=iPhone 13")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/compatibles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductoResponseDTO>> getAccesoriosCompatibles(
            @PathVariable Integer codti, @RequestParam String con,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails user) {
        return ResponseEntity.ok(sinCostosSiAplica(productoService.obtenerAccesoriosCompatibles(codti, con), user));
    }

    @Operation(summary = "Buscar producto por IMEI")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/imei/{imei}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductoResponseDTO> getProductoPorImei(@PathVariable String imei,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails user) {
        return ResponseEntity.ok(sinCostos(productoService.obtenerProductoPorImei(imei), user));
    }

    @Operation(summary = "Registrar producto en tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<ProductoResponseDTO> crearProducto(
            @Valid @RequestBody ProductoRequestDTO request) {
        return ResponseEntity.ok(productoService.crearProducto(request));
    }

    @Operation(summary = "Actualizar precios y datos de un producto")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/{codpro}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<ProductoResponseDTO> actualizarProducto(
            @PathVariable String codpro,
            @Parameter(description = "Sucursal del producto; obligatoria si el código existe en varias.")
            @RequestParam(required = false) Integer codti,
            @Parameter(description = "Aplicar el nuevo precio de compra/venta a este código en TODAS las sucursales que lo tienen.")
            @RequestParam(defaultValue = "false") boolean aTodasLasSucursales,
            @RequestBody ProductoUpdateDTO request) {
        return ResponseEntity.ok(productoService.actualizar(codpro, codti, aTodasLasSucursales, request));
    }

    @Operation(summary = "Ajustar stock — ENTRADA / SALIDA / AJUSTE")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{codpro}/stock")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<ProductoResponseDTO> ajustarStock(
            @PathVariable String codpro,
            @Parameter(description = "Sucursal del producto; obligatoria si el código existe en varias.")
            @RequestParam(required = false) Integer codti,
            @RequestBody StockAjusteDTO request) {
        return ResponseEntity.ok(productoService.ajustarStock(codpro, codti, request));
    }

    @Operation(summary = "Fijar (o quitar) el precio propio de un IMEI específico, distinto al del modelo")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/imei/{imei}/precio")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<com.skycel.backend.domain.dto.response.ImeiInfoDTO> actualizarPrecioImei(
            @PathVariable String imei,
            @RequestBody com.skycel.backend.domain.dto.request.ImeiPrecioUpdateDTO request) {
        return ResponseEntity.ok(productoService.actualizarPrecioImei(imei, request));
    }

    @Operation(summary = "Corregir la condición (NUEVO/USADO/REACONDICIONADO) o el costo de una unidad disponible")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/imei/{imei}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
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
    public ResponseEntity<Void> eliminarProducto(
            @PathVariable String codpro,
            @Parameter(description = "Sucursal del producto; obligatoria si el código existe en varias.")
            @RequestParam(required = false) Integer codti) {
        productoService.eliminarProducto(codpro, codti);
        return ResponseEntity.noContent().build();
    }
}
