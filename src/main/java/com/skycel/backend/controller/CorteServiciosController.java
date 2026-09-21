package com.skycel.backend.controller;

import com.skycel.backend.dto.servicios.*;
import com.skycel.backend.service.CorteServiciosService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cortes-servicios")
@RequiredArgsConstructor
@Tag(name = "Corte de servicios", description = "Corte diario de recargas, pagos de servicios, pines y pagos PayJoy (separado de ventas y caja)")
public class CorteServiciosController {

    private static final String SUPERIOR = "hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')";
    private static final String ADMIN = "hasAnyRole('ROOT','ADMIN')";

    private final CorteServiciosService corteService;

    // ── Catálogo de servicios ────────────────────────────────────────────────

    @Operation(summary = "Servicios del corte", description = "Por omisión solo los activos. Cada uno con su comisión por operación.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tipos")
    @PreAuthorize(SUPERIOR)
    public ResponseEntity<StandardApiResponse<List<ServicioTipoDto>>> tipos(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return ResponseEntityBuilder.ok(corteService.tipos(incluirInactivos), "servicios");
    }

    @Operation(summary = "Agregar un servicio al corte")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/tipos")
    @PreAuthorize(ADMIN)
    public ResponseEntity<StandardApiResponse<ServicioTipoDto>> crearTipo(@Valid @RequestBody ServicioTipoDto request) {
        return ResponseEntityBuilder.created(corteService.crearTipo(request), "servicio");
    }

    @Operation(summary = "Cambiar el nombre, la comisión o el estado de un servicio")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/tipos/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<StandardApiResponse<ServicioTipoDto>> actualizarTipo(@PathVariable Integer id, @Valid @RequestBody ServicioTipoDto request) {
        return ResponseEntityBuilder.updated(corteService.actualizarTipo(id, request), "servicio");
    }

    // ── Cortes ───────────────────────────────────────────────────────────────

    @Operation(summary = "Capturar el corte del día",
            description = "Por servicio: operaciones, importe y (opcional) saldos del proveedor. Si ya hay uno sin confirmar de ese día, lo reemplaza.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize(SUPERIOR)
    public ResponseEntity<StandardApiResponse<CorteServiciosResponseDto>> guardar(
            @Valid @RequestBody CorteServiciosRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(corteService.guardar(request, user.getUsername()), "corte");
    }

    @Operation(summary = "El corte de un día (null si aún no se captura)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/dia")
    @PreAuthorize(SUPERIOR)
    public ResponseEntity<StandardApiResponse<CorteServiciosResponseDto>> delDia(
            @RequestParam(required = false) Integer codti,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(corteService.delDia(codti, fecha, user.getUsername()), "corte");
    }

    @Operation(summary = "Listar cortes",
            description = "Por omisión, los últimos 30 días. Un administrador ve todas las sucursales (o la que indique); un encargado, la suya.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize(SUPERIOR)
    public ResponseEntity<StandardApiResponse<List<CorteServiciosResponseDto>>> listar(
            @RequestParam(required = false) Integer codti,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Byte estado,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(corteService.listar(codti, desde, hasta, estado, user.getUsername()), "cortes");
    }

    @Operation(summary = "Totales por servicio de un periodo", description = "Por omisión, el mes en curso.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/resumen")
    @PreAuthorize(SUPERIOR)
    public ResponseEntity<StandardApiResponse<ResumenServiciosResponseDto>> resumen(
            @RequestParam(required = false) Integer codti,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(corteService.resumen(codti, desde, hasta, user.getUsername()), "resumen");
    }

    @Operation(summary = "Consultar un corte")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}")
    @PreAuthorize(SUPERIOR)
    public ResponseEntity<StandardApiResponse<CorteServiciosResponseDto>> obtener(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(corteService.obtener(id, user.getUsername()), "corte");
    }

    @Operation(summary = "Confirmar un corte con lo que reporta cada proveedor",
            description = "Sin diferencias queda Confirmado; con diferencias exige un comentario y queda Con diferencias.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/confirmar")
    @PreAuthorize(ADMIN)
    public ResponseEntity<StandardApiResponse<CorteServiciosResponseDto>> confirmar(
            @PathVariable Integer id, @Valid @RequestBody ConfirmarCorteRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(corteService.confirmar(id, request, user.getUsername()), "corte");
    }

    @Operation(summary = "Reabrir un corte confirmado para corregirlo")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/reabrir")
    @PreAuthorize(ADMIN)
    public ResponseEntity<StandardApiResponse<CorteServiciosResponseDto>> reabrir(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(corteService.reabrir(id, user.getUsername()), "corte");
    }
}
