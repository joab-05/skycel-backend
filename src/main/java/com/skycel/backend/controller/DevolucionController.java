package com.skycel.backend.controller;

import com.skycel.backend.dto.devolucion.*;
import com.skycel.backend.service.DevolucionService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devoluciones")
@RequiredArgsConstructor
@Tag(name = "Devoluciones", description = "Devolución de productos vendidos: reembolso o cambio, con aprobación de un encargado")
public class DevolucionController {

    private static final String PERSONAL = "hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA','VENDEDOR')";
    private static final String SUPERIOR = "hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')";

    private final DevolucionService devolucionService;

    @Operation(summary = "¿Qué se puede devolver de esta venta?",
            description = "Indica si la venta está en plazo (15 días) y, por renglón, cuánto queda por devolver.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/venta/{idventa}")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<VentaDevolvibleResponseDto>> consultarVenta(
            @PathVariable Integer idventa, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(devolucionService.consultarVenta(idventa, user.getUsername()), "venta");
    }

    @Operation(summary = "Solicitar una devolución",
            description = "Queda Pendiente hasta que un encargado de la tienda o un administrador la apruebe.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<DevolucionResponseDto>> solicitar(
            @Valid @RequestBody DevolucionRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(devolucionService.solicitar(request, user.getUsername()), "devolución");
    }

    @Operation(summary = "Aprobar una devolución",
            description = "La ejecuta: regresa lo devuelto al inventario y hace el reembolso o el cambio.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/aprobar")
    @PreAuthorize(SUPERIOR)
    public ResponseEntity<StandardApiResponse<DevolucionResponseDto>> aprobar(
            @PathVariable Integer id, @Valid @RequestBody(required = false) ResolverDevolucionDto request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(devolucionService.aprobar(id, request, user.getUsername()), "devolución");
    }

    @Operation(summary = "Rechazar una devolución", description = "El comentario con el motivo es obligatorio.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/rechazar")
    @PreAuthorize(SUPERIOR)
    public ResponseEntity<StandardApiResponse<DevolucionResponseDto>> rechazar(
            @PathVariable Integer id, @Valid @RequestBody ResolverDevolucionDto request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(devolucionService.rechazar(id, request, user.getUsername()), "devolución");
    }

    @Operation(summary = "Listar devoluciones",
            description = "Un administrador ve todas las tiendas (o la que indique con codti); el resto, la suya. Filtro opcional por estado " +
                    "(1 Pendiente, 2 Procesada, 3 Rechazada).")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<List<DevolucionResponseDto>>> listar(
            @RequestParam(required = false) Integer codti, @RequestParam(required = false) Byte estado,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(devolucionService.listar(codti, estado, user.getUsername()), "devoluciones");
    }

    @Operation(summary = "Consultar una devolución")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<DevolucionResponseDto>> obtener(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(devolucionService.obtener(id, user.getUsername()), "devolución");
    }
}
