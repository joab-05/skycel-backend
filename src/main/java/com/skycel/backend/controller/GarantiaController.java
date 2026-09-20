package com.skycel.backend.controller;

import com.skycel.backend.dto.garantia.*;
import com.skycel.backend.service.GarantiaService;
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
@RequestMapping("/api/garantias")
@RequiredArgsConstructor
@Tag(name = "Garantías", description = "Reclamos de garantía de productos vendidos, con viaje al proveedor")
public class GarantiaController {

    private static final String PERSONAL = "hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA','VENDEDOR')";

    private final GarantiaService garantiaService;

    @Operation(summary = "¿Este producto de esta venta todavía tiene garantía?",
            description = "Indique idventa y el imei (equipos) o el codpro (accesorios). Útil antes de recibir el equipo.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/elegibilidad")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<ElegibilidadGarantiaResponseDto>> elegibilidad(
            @RequestParam Integer idventa, @RequestParam(required = false) String imei,
            @RequestParam(required = false) String codpro, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(garantiaService.elegibilidad(idventa, imei, codpro, user.getUsername()), "elegibilidad");
    }

    @Operation(summary = "Recibir un producto por garantía",
            description = "Valida que la venta siga vigente, que el producto aparezca en ella y que no haya vencido su garantía. " +
                    "Genera el folio de seguimiento (GAR-000001) y la fecha límite (30 días hábiles).")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<GarantiaResponseDto>> crear(
            @Valid @RequestBody GarantiaRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(garantiaService.crear(request, user.getUsername()), "garantía");
    }

    @Operation(summary = "Avanzar el estado de una garantía",
            description = "2 En tránsito a bodega, 3 Recibido en bodega, 4 En tránsito al proveedor, 5 En el proveedor, 6 Reparado, " +
                    "7 Cambio físico, 8 Rechazado, 9 Viaje de retorno, 10 Listo para entrega, 11 Entregado. La sucursal registra " +
                    "sus pasos (enviar, rechazar en sucursal, recibir de regreso y entregar); bodega y proveedor, un administrador.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/avanzar")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<GarantiaResponseDto>> avanzar(
            @PathVariable Integer id, @Valid @RequestBody AvanceGarantiaRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(garantiaService.avanzar(id, request, user.getUsername()), "garantía");
    }

    @Operation(summary = "Consultar una garantía")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<GarantiaResponseDto>> obtener(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(garantiaService.obtener(id, user.getUsername()), "garantía");
    }

    @Operation(summary = "Consultar una garantía por su folio (GAR-000001)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/folio/{folio}")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<GarantiaResponseDto>> obtenerPorFolio(
            @PathVariable String folio, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(garantiaService.obtenerPorFolio(folio, user.getUsername()), "garantía");
    }

    @Operation(summary = "Listar garantías",
            description = "Un administrador ve todas las sucursales (o la que indique con codti); el resto, la suya. Filtros: estado, " +
                    "soloAbiertas (no entregadas) y vencidas (pasaron su fecha límite sin entregarse).")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<List<GarantiaResponseDto>>> listar(
            @RequestParam(required = false) Integer codti,
            @RequestParam(required = false) Byte estado,
            @RequestParam(defaultValue = "false") boolean soloAbiertas,
            @RequestParam(defaultValue = "false") boolean vencidas,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(garantiaService.listar(codti, estado, soloAbiertas, vencidas, user.getUsername()), "garantías");
    }
}
