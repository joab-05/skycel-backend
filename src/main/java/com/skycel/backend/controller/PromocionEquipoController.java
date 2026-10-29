package com.skycel.backend.controller;

import com.skycel.backend.dto.promocion.PromocionEquipoDto;
import com.skycel.backend.dto.promocion.PromocionReglasDto;
import com.skycel.backend.service.PromocionEquipoService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * La promoción al vender un equipo (chip de regalo, mica y protector con descuento). Cualquiera con sesión la consulta, porque el
 * punto de venta la muestra al armar la venta; solo quien define descuentos la cambia.
 */
@RestController
@RequestMapping("/api/promociones/equipo")
@RequiredArgsConstructor
@Tag(name = "Promociones", description = "Promoción al vender un equipo")
public class PromocionEquipoController {

    private final PromocionEquipoService promocionEquipoService;

    @GetMapping
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<PromocionEquipoDto>> obtener() {
        return ResponseEntityBuilder.ok(promocionEquipoService.obtener(), "promocion");
    }

    @GetMapping("/reglas")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<PromocionReglasDto>> reglas() {
        return ResponseEntityBuilder.ok(promocionEquipoService.obtenerReglas(), "reglas de la promocion");
    }

    @PutMapping("/reglas")
    @PreAuthorize("@permisos.tiene('DESCUENTOS')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<PromocionReglasDto>> guardarReglas(@RequestBody PromocionReglasDto dto) {
        return ResponseEntityBuilder.updated(promocionEquipoService.guardarReglas(dto), "reglas de la promocion");
    }

    @PutMapping
    @PreAuthorize("@permisos.tiene('DESCUENTOS')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<PromocionEquipoDto>> actualizar(@RequestBody PromocionEquipoDto dto) {
        return ResponseEntityBuilder.updated(promocionEquipoService.actualizar(dto), "promocion");
    }
}
