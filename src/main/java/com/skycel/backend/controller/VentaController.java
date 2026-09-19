package com.skycel.backend.controller;

import com.skycel.backend.dto.venta.VentaRequestDto;
import com.skycel.backend.dto.venta.VentaResponseDto;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.service.VentaService;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@Tag(name = "Ventas", description = "Registro y consulta de ventas")
public class VentaController {

    private final VentaService ventaService;
    private final UsuarioRepository usuarioRepository;

    @Operation(summary = "Registrar una venta")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA','VENDEDOR')")
    public ResponseEntity<StandardApiResponse<VentaResponseDto>> crear(
            @Valid @RequestBody VentaRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Integer idVendedor = idUsuarioAutenticado(userDetails);
        return ResponseEntityBuilder.created(ventaService.crear(request, idVendedor), "venta");
    }

    @Operation(summary = "Obtener una venta por ID")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<VentaResponseDto>> obtener(@PathVariable Integer id) {
        return ResponseEntityBuilder.ok(ventaService.obtenerPorId(id), "venta");
    }

    @Operation(summary = "Listar ventas de una tienda en un rango de fechas (por defecto, el día de hoy)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<List<VentaResponseDto>>> listarPorTienda(
            @PathVariable Integer codti,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntityBuilder.ok(ventaService.listarPorTienda(codti, desde, hasta), "ventas");
    }

    @Operation(summary = "Listar ventas de un cliente")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/cliente/{idcliente}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<List<VentaResponseDto>>> listarPorCliente(
            @PathVariable Integer idcliente) {
        return ResponseEntityBuilder.ok(ventaService.listarPorCliente(idcliente), "ventas");
    }

    @Operation(summary = "Cancelar una venta (revierte stock e IMEIs)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<VentaResponseDto>> cancelar(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.updated(ventaService.cancelar(id, idUsuarioAutenticado(userDetails)), "venta");
    }

    private Integer idUsuarioAutenticado(UserDetails userDetails) {
        return usuarioRepository.findByUsername(userDetails.getUsername())
                .map(u -> u.getIdusuario())
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado no encontrado"));
    }
}
