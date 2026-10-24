package com.skycel.backend.controller;

import com.skycel.backend.dto.caja.AbrirCajaRequestDto;
import com.skycel.backend.dto.caja.CerrarCajaRequestDto;
import com.skycel.backend.dto.caja.SesionCajaResponseDto;
import com.skycel.backend.dto.caja.CajaRequestDto;
import com.skycel.backend.dto.caja.CajaResponseDto;
import com.skycel.backend.dto.caja.MovimientoCajaRequestDto;
import com.skycel.backend.dto.caja.MovimientoCajaResponseDto;
import com.skycel.backend.dto.caja.SaldoCajaResponseDto;
import com.skycel.backend.service.CajaService;
import com.skycel.backend.service.MovimientoCajaService;
import com.skycel.backend.service.SesionCajaService;
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

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/cajas")
@RequiredArgsConstructor
@Tag(name = "Cajas", description = "Cajas registradoras por tienda y sus movimientos de efectivo")
public class CajaController {

    private final CajaService cajaService;
    private final MovimientoCajaService movimientoCajaService;
    private final SesionCajaService sesionCajaService;

    @Operation(summary = "Cajas de una tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CajaResponseDto>> listarPorTienda(@PathVariable Integer codti) {
        return ResponseEntity.ok(cajaService.listarPorTienda(codti));
    }

    @Operation(summary = "Da de alta una caja nueva en una sucursal")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("@permisos.tiene('CAJA_CONFIG')")
    public ResponseEntity<StandardApiResponse<CajaResponseDto>> crear(@Valid @RequestBody CajaRequestDto request) {
        return ResponseEntityBuilder.created(cajaService.crear(request), "caja");
    }

    @Operation(summary = "Registrar un movimiento manual de efectivo (fondo, gasto, retiro, etc.)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{idCaja}/movimientos")
    @PreAuthorize("@permisos.tiene('CAJA_MOVIMIENTOS')")
    public ResponseEntity<StandardApiResponse<MovimientoCajaResponseDto>> registrarMovimiento(
            @PathVariable Integer idCaja,
            @Valid @RequestBody MovimientoCajaRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.created(
                movimientoCajaService.registrarManual(idCaja, request, userDetails.getUsername()), "movimiento");
    }

    @Operation(summary = "Movimientos de una caja (por defecto, los de hoy)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{idCaja}/movimientos")
    @PreAuthorize("@permisos.tiene('CAJA_MOVIMIENTOS')")
    public ResponseEntity<StandardApiResponse<List<MovimientoCajaResponseDto>>> listarMovimientos(
            @PathVariable Integer idCaja,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(
                movimientoCajaService.listar(idCaja, desde, hasta, userDetails.getUsername()), "movimientos");
    }

    @Operation(summary = "Saldo de una caja (entradas - salidas)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{idCaja}/saldo")
    @PreAuthorize("@permisos.tiene('CAJA_MOVIMIENTOS')")
    public ResponseEntity<StandardApiResponse<SaldoCajaResponseDto>> saldo(
            @PathVariable Integer idCaja, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(movimientoCajaService.saldo(idCaja, userDetails.getUsername()), "saldo");
    }

    // ── Turnos de caja: abrir al iniciar el día y cerrar al terminar (corte) ─────────────────────

    @Operation(summary = "Turno actual de la caja",
            description = "Si está abierta: el turno con su resumen en vivo. Si está cerrada: abierta=false y el fondo sugerido para abrir.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{idCaja}/sesion")
    @PreAuthorize("@permisos.tiene('CAJA_OPERAR')")
    public ResponseEntity<StandardApiResponse<SesionCajaResponseDto>> sesionActual(
            @PathVariable Integer idCaja, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(sesionCajaService.actual(idCaja, userDetails.getUsername()), "sesion de caja");
    }

    @Operation(summary = "Abrir la caja con su fondo inicial")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{idCaja}/abrir")
    @PreAuthorize("@permisos.tiene('CAJA_OPERAR')")
    public ResponseEntity<StandardApiResponse<SesionCajaResponseDto>> abrir(
            @PathVariable Integer idCaja, @Valid @RequestBody AbrirCajaRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.created(sesionCajaService.abrir(idCaja, request, userDetails.getUsername()), "sesion de caja");
    }

    @Operation(summary = "Cerrar la caja: genera y guarda el corte")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{idCaja}/cerrar")
    @PreAuthorize("@permisos.tiene('CAJA_OPERAR')")
    public ResponseEntity<StandardApiResponse<SesionCajaResponseDto>> cerrar(
            @PathVariable Integer idCaja, @Valid @RequestBody CerrarCajaRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(sesionCajaService.cerrar(idCaja, request, userDetails.getUsername()), "corte de caja");
    }

    @Operation(summary = "Historial de turnos (cortes) de la caja, el más reciente primero")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{idCaja}/sesiones")
    @PreAuthorize("@permisos.tiene('CAJA_OPERAR')")
    public ResponseEntity<StandardApiResponse<List<SesionCajaResponseDto>>> sesiones(
            @PathVariable Integer idCaja, @RequestParam(defaultValue = "60") int limite,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(sesionCajaService.historial(idCaja, limite, userDetails.getUsername()), "turnos de caja");
    }

    @Operation(summary = "Un turno con su corte completo")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/sesiones/{idsesion}")
    @PreAuthorize("@permisos.tiene('CAJA_OPERAR')")
    public ResponseEntity<StandardApiResponse<SesionCajaResponseDto>> sesion(
            @PathVariable Integer idsesion, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(sesionCajaService.obtener(idsesion, userDetails.getUsername()), "turno de caja");
    }
}
