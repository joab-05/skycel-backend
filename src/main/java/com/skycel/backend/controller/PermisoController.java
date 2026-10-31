package com.skycel.backend.controller;

import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.service.PermisoService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Qué puede hacer cada rol: cualquiera consulta los suyos; solo root los cambia (o quien root autorice). */
@RestController
@RequestMapping("/api/permisos")
@RequiredArgsConstructor
@Tag(name = "Permisos", description = "Qué puede hacer cada tipo de usuario")
public class PermisoController {

    private final PermisoService permisoService;
    private final UsuarioRepository usuarioRepository;

    @Data
    public static class AsignacionDto {
        /** Roles que quedan con el permiso (ADMIN, ENCARGADO_TIENDA, VENDEDOR, TECNICO). */
        private List<String> roles;
    }

    @Operation(summary = "Los permisos de quien consulta (para mostrar u ocultar secciones y botones)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/mios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> mios(@AuthenticationPrincipal UserDetails user) {
        Rol rol = user.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .map(n -> { try { return Rol.valueOf(n); } catch (IllegalArgumentException e) { return null; } })
                .filter(r -> r != null).findFirst().orElse(null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rol", rol != null ? rol.name() : null);
        r.put("permisos", usuarioRepository.findByUsername(user.getUsername())
                .map(permisoService::deUsuario).orElseGet(() -> rol != null ? permisoService.deRol(rol) : List.of()));
        return ResponseEntityBuilder.ok(r, "permisos");
    }

    @Operation(summary = "Todos los permisos con los roles que los tienen (root o quien root autorice)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/catalogo")
    @PreAuthorize("hasRole('ROOT') or @adminPermisos.puede()")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> catalogo() {
        return ResponseEntityBuilder.ok(permisoService.catalogo(), "permisos");
    }

    @Operation(summary = "Cambia qué roles tienen un permiso (root o quien root autorice)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/{codigo}")
    @PreAuthorize("hasRole('ROOT') or @adminPermisos.puede()")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> asignar(@PathVariable String codigo, @RequestBody AsignacionDto dto) {
        return ResponseEntityBuilder.updated(permisoService.asignar(codigo, dto.getRoles()), "permiso");
    }
}
