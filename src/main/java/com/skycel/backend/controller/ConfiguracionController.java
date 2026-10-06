package com.skycel.backend.controller;

import com.skycel.backend.dto.configuracion.ConfiguracionNegocioDto;
import com.skycel.backend.service.ConfiguracionNegocioService;
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

@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
@Tag(name = "Configuración", description = "Datos del negocio compartidos por todas las tiendas")
public class ConfiguracionController {

    private final ConfiguracionNegocioService configuracionService;

    @Operation(summary = "Datos del negocio",
            description = "Nombre comercial, razón social, RFC, pie y leyenda del ticket y plazo de devoluciones. Los lee cualquier usuario " +
                    "(los tickets los necesitan).")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/negocio")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<ConfiguracionNegocioDto>> obtener() {
        return ResponseEntityBuilder.ok(configuracionService.obtener(), "datos del negocio");
    }

    @Operation(summary = "Logo del negocio (público)",
            description = "Imagen PNG. Es pública porque la pantalla de acceso la muestra antes de iniciar sesión. 404 si no hay logo.")
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(org.springframework.web.context.request.WebRequest request) {
        java.util.Optional<byte[]> logo = configuracionService.logo();
        if (logo.isEmpty()) return ResponseEntity.notFound().build();
        String etag = "\"" + configuracionService.logoVersion() + "\"";
        if (request.checkNotModified(etag)) return null;
        return ResponseEntity.ok().eTag(etag).header("Cache-Control", "no-cache").contentType(org.springframework.http.MediaType.IMAGE_PNG).body(logo.get());
    }

    @Operation(summary = "Si hay logo y su versión (público)")
    @GetMapping("/logo/info")
    public ResponseEntity<java.util.Map<String, Object>> logoInfo() {
        boolean tiene = configuracionService.logo().isPresent();
        return ResponseEntity.ok(java.util.Map.of("tiene", tiene, "version", tiene ? configuracionService.logoVersion() : 0L));
    }

    @Operation(summary = "Cambiar el logo", description = "Cuerpo: la imagen PNG o JPEG (hasta 2 MB). Se reduce a 512 px y se guarda como PNG.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping(value = "/logo", consumes = {"image/png", "image/jpeg"})
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> cambiarLogo(@RequestBody byte[] imagen, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(java.util.Map.of("version", configuracionService.guardarLogo(imagen, user.getUsername())));
    }

    @Operation(summary = "Quitar el logo", description = "Cada aplicación vuelve a su ícono por omisión.")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/logo")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<java.util.Map<String, String>> quitarLogo(@AuthenticationPrincipal UserDetails user) {
        configuracionService.quitarLogo(user.getUsername());
        return ResponseEntity.ok(java.util.Map.of("mensaje", "Logo quitado."));
    }

    @Operation(summary = "Guardar los datos del negocio")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/negocio")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<ConfiguracionNegocioDto>> actualizar(
            @Valid @RequestBody ConfiguracionNegocioDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(configuracionService.actualizar(request, user.getUsername()), "datos del negocio");
    }
}
