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
    private final com.skycel.backend.service.PermisoService permisoService;
    private final com.skycel.backend.service.ExistenciaTiendasService existenciaTiendasService;

    /** Quién puede ver cuánto costó la mercancía: los roles con el permiso COSTOS_VER (por omisión administradores y encargados). */
    private boolean veCostos(org.springframework.security.core.userdetails.UserDetails u) {
        return u != null && permisoService.tiene(com.skycel.backend.domain.enums.Permiso.COSTOS_VER.name());
    }
    private ProductoResponseDTO sinCostos(ProductoResponseDTO d, org.springframework.security.core.userdetails.UserDetails u) {
        if (veCostos(u)) return d;
        d.setPreciopro(null);
        if (d.getImeisDisponibles() != null) {
            d.getImeisDisponibles().forEach(i -> { i.setCostoUnitario(null); i.setTieneCostoPropio(null); });
        }
        return d;
    }
    private List<ProductoResponseDTO> sinCostosSiAplica(List<ProductoResponseDTO> lista, org.springframework.security.core.userdetails.UserDetails u) {
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
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
    public ResponseEntity<ProductoMasterResponseDTO> crearMaster(
            @Valid @RequestBody ProductoMasterRequestDTO request) {
        return ResponseEntity.ok(productoService.crearMaster(request));
    }

    @Operation(summary = "Desactivar Producto Maestro")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/master/{id}")
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
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

    @Operation(summary = "Existencias de un artículo en todas las sucursales",
            description = "Dónde hay existencia de lo que se busca (nombre, código o IMEI/serie; mínimo 2 letras), para pedirlo en un traspaso. " +
                    "Sin costos. Con excluir se omite la sucursal de quien busca.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/existencias")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<com.skycel.backend.dto.producto.ExistenciaTiendaDto>> existenciasEnTiendas(
            @RequestParam String texto, @RequestParam(required = false) Integer excluir) {
        return ResponseEntity.ok(existenciaTiendasService.buscar(texto, excluir));
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
    @PreAuthorize("@permisos.tiene('INVENTARIO_AJUSTAR')")
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

    @Operation(summary = "Registrar producto en tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
    public ResponseEntity<ProductoResponseDTO> crearProducto(
            @Valid @RequestBody ProductoRequestDTO request) {
        return ResponseEntity.ok(productoService.crearProducto(request));
    }

    @Operation(summary = "Actualizar precios y datos de un producto")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/{codpro}")
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
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
    @PreAuthorize("@permisos.tiene('INVENTARIO_AJUSTAR')")
    public ResponseEntity<ProductoResponseDTO> ajustarStock(
            @PathVariable String codpro,
            @Parameter(description = "Sucursal del producto; obligatoria si el código existe en varias.")
            @RequestParam(required = false) Integer codti,
            @RequestBody StockAjusteDTO request) {
        return ResponseEntity.ok(productoService.ajustarStock(codpro, codti, request));
    }

    @Operation(summary = "Marcar (o desmarcar) un artículo como rezagado a mano")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{codpro}/rezagado")
    @PreAuthorize("@permisos.tiene('INVENTARIO_AJUSTAR')")
    public ResponseEntity<ProductoResponseDTO> marcarRezagado(
            @PathVariable String codpro,
            @Parameter(description = "Sucursal del producto; obligatoria si el código existe en varias.")
            @RequestParam(required = false) Integer codti,
            @RequestParam boolean rezagado) {
        return ResponseEntity.ok(productoService.marcarRezagado(codpro, codti, rezagado));
    }

    @Operation(summary = "Actualizar compatibilidad, tiempo estimado o garantía de un producto maestro")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/master/{id}")
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
    public ResponseEntity<ProductoMasterResponseDTO> actualizarMaster(
            @PathVariable Integer id, @Valid @RequestBody ProductoMasterUpdateDTO request) {
        return ResponseEntity.ok(productoService.actualizarMaster(id, request));
    }

    @Operation(summary = "Desactivar producto (soft delete)")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{codpro}")
    @PreAuthorize("@permisos.tiene('INVENTARIO_EDITAR')")
    public ResponseEntity<Void> eliminarProducto(
            @PathVariable String codpro,
            @Parameter(description = "Sucursal del producto; obligatoria si el código existe en varias.")
            @RequestParam(required = false) Integer codti) {
        productoService.eliminarProducto(codpro, codti);
        return ResponseEntity.noContent().build();
    }
}
