package com.skycel.backend.controller;

import com.skycel.backend.dto.ordenservicio.*;
import com.skycel.backend.service.OrdenServicioService;
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
@RequestMapping("/api/ordenes-servicio")
@RequiredArgsConstructor
@Tag(name = "Órdenes de servicio", description = "Taller: recepción de equipos, reparación, anticipos y entrega")
public class OrdenServicioController {

    private static final String PERSONAL = "hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA','VENDEDOR')";
    private static final String PERSONAL_Y_TECNICO = "hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA','VENDEDOR','TECNICO')";

    private final OrdenServicioService ordenService;

    @Operation(summary = "Recibir un equipo (crear una orden de servicio)",
            description = "Opcionalmente con técnico, renglones (servicios y refacciones) y un anticipo.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> crear(
            @Valid @RequestBody OrdenServicioRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(ordenService.crear(request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Consultar una orden")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}")
    @PreAuthorize(PERSONAL_Y_TECNICO)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> obtener(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.obtener(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Consultar una orden por su folio (OS-000001)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/folio/{folio}")
    @PreAuthorize(PERSONAL_Y_TECNICO)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> obtenerPorFolio(
            @PathVariable String folio, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.obtenerPorFolio(folio, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Órdenes de una tienda",
            description = "Filtros opcionales: estado (1 Recibida, 2 En reparación, 3 Lista, 4 Entregada, 5 Cancelada) y técnico.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<List<OrdenServicioResponseDto>>> listar(
            @PathVariable Integer codti,
            @RequestParam(required = false) Byte estado,
            @RequestParam(required = false) Integer idTecnico,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.listar(codti, estado, idTecnico, user.getUsername()), "órdenes de servicio");
    }

    @Operation(summary = "Mis órdenes abiertas (para un técnico)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/mis-ordenes")
    @PreAuthorize("hasRole('TECNICO')")
    public ResponseEntity<StandardApiResponse<List<OrdenServicioResponseDto>>> misOrdenes(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.misOrdenes(user.getUsername()), "órdenes de servicio");
    }

    @Operation(summary = "Actualizar los datos de una orden abierta (incluye el diagnóstico)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{id}")
    @PreAuthorize(PERSONAL_Y_TECNICO)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> actualizar(
            @PathVariable Integer id, @Valid @RequestBody OrdenActualizarDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.actualizar(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Asignar (o cambiar) el técnico")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/tecnico")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> asignarTecnico(
            @PathVariable Integer id, @Valid @RequestBody AsignarTecnicoDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.asignarTecnico(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Agregar un servicio o una refacción a la orden")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/lineas")
    @PreAuthorize(PERSONAL_Y_TECNICO)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> agregarLinea(
            @PathVariable Integer id, @Valid @RequestBody OrdenLineaRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.agregarLinea(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Quitar un renglón de la orden")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{id}/lineas/{iddetalle}")
    @PreAuthorize(PERSONAL_Y_TECNICO)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> eliminarLinea(
            @PathVariable Integer id, @PathVariable Integer iddetalle, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.eliminarLinea(id, iddetalle, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Iniciar la reparación", description = "Requiere un técnico asignado.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/iniciar")
    @PreAuthorize(PERSONAL_Y_TECNICO)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> iniciar(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.iniciar(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Marcar la orden como lista para entrega",
            description = "Exige al menos un renglón y stock de las refacciones.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/lista")
    @PreAuthorize(PERSONAL_Y_TECNICO)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> lista(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.marcarLista(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Reabrir una orden lista (vuelve a reparación)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/reabrir")
    @PreAuthorize(PERSONAL_Y_TECNICO)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> reabrir(
            @PathVariable Integer id, @Valid @RequestBody ComentarioRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.reabrir(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Registrar un anticipo", description = "Solo el efectivo entra a la caja.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/anticipos")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> registrarAnticipo(
            @PathVariable Integer id, @Valid @RequestBody AnticipoRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.registrarAnticipo(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Entregar el equipo y cobrar el saldo",
            description = "Genera la venta de la orden. El anticipo ya cobrado no vuelve a entrar a la caja.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/entregar")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> entregar(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) EntregaRequestDto request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(
                ordenService.entregar(id, request != null ? request : new EntregaRequestDto(), user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Cancelar la orden", description = "Opcionalmente devuelve los anticipos en efectivo.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/cancelar")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> cancelar(
            @PathVariable Integer id, @Valid @RequestBody CancelarOrdenRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.cancelar(id, request, user.getUsername()), "orden de servicio");
    }
}
