package com.skycel.backend.controller;

import com.skycel.backend.dto.usuario.CambiarPasswordDto;
import com.skycel.backend.dto.usuario.UsuarioCreateDto;
import com.skycel.backend.dto.usuario.UsuarioResponseDto;
import com.skycel.backend.dto.usuario.UsuarioUpdateDto;
import com.skycel.backend.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de gestión de usuarios / empleados.
 * Base: /api/usuarios
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    // ── GET /api/usuarios ─────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ResponseEntity<List<UsuarioResponseDto>> listarTodos(
            @RequestParam(value = "soloActivos", defaultValue = "false") boolean soloActivos) {

        List<UsuarioResponseDto> lista = soloActivos
                ? usuarioService.listarActivos()
                : usuarioService.listarTodos();

        return ResponseEntity.ok(lista);
    }

    // ── GET /api/usuarios/tecnicos ────────────────────────────────────────────

    /** Técnicos activos, para asignarlos a las órdenes de servicio (también lo usa un encargado). */
    @GetMapping("/tecnicos")
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN', 'ENCARGADO_TIENDA', 'TECNICO')")
    public ResponseEntity<List<UsuarioResponseDto>> listarTecnicos(
            @RequestParam(value = "codti", required = false) Integer codti,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(usuarioService.listarTecnicos(codti, user.getUsername()));
    }

    // ── GET /api/usuarios/{id} ────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ResponseEntity<UsuarioResponseDto> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    // ── POST /api/usuarios ────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ResponseEntity<UsuarioResponseDto> crear(@RequestBody UsuarioCreateDto dto) {
        UsuarioResponseDto creado = usuarioService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // ── PUT /api/usuarios/{id} ────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ResponseEntity<UsuarioResponseDto> actualizar(
            @PathVariable Integer id,
            @RequestBody UsuarioUpdateDto dto) {
        return ResponseEntity.ok(usuarioService.actualizar(id, dto));
    }

    // ── PATCH /api/usuarios/mi-password ───────────────────────────────────────

    /** Cualquier usuario autenticado cambia su propia contraseña (pide la actual). */
    @PatchMapping("/mi-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cambiarMiPassword(
            @Valid @RequestBody CambiarPasswordDto dto,
            @AuthenticationPrincipal UserDetails user) {
        usuarioService.cambiarPasswordPropia(user.getUsername(), dto.getPasswordActual(), dto.getPasswordNueva());
        return ResponseEntity.noContent().build();
    }

    // ── DELETE /api/usuarios/{id} (soft delete) ───────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ResponseEntity<Void> desactivar(@PathVariable Integer id) {
        usuarioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}