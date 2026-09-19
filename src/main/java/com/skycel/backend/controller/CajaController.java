package com.skycel.backend.controller;

import com.skycel.backend.dto.caja.CajaResponseDto;
import com.skycel.backend.dto.caja.MovimientoCajaRequestDto;
import com.skycel.backend.dto.caja.MovimientoCajaResponseDto;
import com.skycel.backend.dto.caja.SaldoCajaResponseDto;
import com.skycel.backend.service.CajaService;
import com.skycel.backend.service.MovimientoCajaService;
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

    @Operation(summary = "Cajas de una tienda")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CajaResponseDto>> listarPorTienda(@PathVariable Integer codti) {
        return ResponseEntity.ok(cajaService.listarPorTienda(codti));
    }

    @Operation(summary = "Registrar un movimiento manual de efectivo (fondo, gasto, retiro, etc.)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{idCaja}/movimientos")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
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
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
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
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<SaldoCajaResponseDto>> saldo(
            @PathVariable Integer idCaja, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(movimientoCajaService.saldo(idCaja, userDetails.getUsername()), "saldo");
    }
}
