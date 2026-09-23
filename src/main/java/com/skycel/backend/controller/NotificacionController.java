package com.skycel.backend.controller;

import com.skycel.backend.dto.notificacion.NotificacionResponseDto;
import com.skycel.backend.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Avisos por usuario de cambios en garantías, órdenes de servicio y traspasos")
public class NotificacionController {

    private final NotificacionService notificacionService;

    @Operation(summary = "Listar mis notificaciones", description = "Por defecto, solo las no leídas; con todas=true, las últimas 100.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificacionResponseDto>> listar(
            @RequestParam(defaultValue = "false") boolean todas,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(todas ? notificacionService.listarTodas(user.getUsername())
                : notificacionService.listarPendientes(user.getUsername()));
    }

    @Operation(summary = "Cuántas notificaciones no leídas tengo")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/pendientes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> contarPendientes(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(notificacionService.contarPendientes(user.getUsername()));
    }

    @Operation(summary = "Marcar una notificación como leída")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{id}/leer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marcarLeida(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        notificacionService.marcarLeida(id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marcar todas mis notificaciones como leídas")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/leer-todas")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marcarTodasLeidas(@AuthenticationPrincipal UserDetails user) {
        notificacionService.marcarTodasLeidas(user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
