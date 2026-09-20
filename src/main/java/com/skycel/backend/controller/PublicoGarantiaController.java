package com.skycel.backend.controller;

import com.skycel.backend.dto.garantia.GarantiaPublicaResponseDto;
import com.skycel.backend.service.GarantiaService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Consulta que hace el cliente, sin iniciar sesión (ruta abierta en SecurityConfig). Para que no se puedan ir
 * descubriendo folios, exige el folio y los últimos 4 dígitos del teléfono de contacto; solo devuelve información de
 * seguimiento (nunca datos personales ni comentarios internos). El límite de peticiones por IP aplica igual que en el resto.
 */
@RestController
@RequestMapping("/api/publico/garantias")
@RequiredArgsConstructor
@Tag(name = "Consulta pública", description = "Seguimiento de una garantía por el cliente, sin iniciar sesión")
public class PublicoGarantiaController {

    private final GarantiaService garantiaService;

    @Operation(summary = "Seguimiento de una garantía",
            description = "Folio (GAR-000001) y últimos 4 dígitos del teléfono de contacto.")
    @GetMapping("/{folio}")
    public ResponseEntity<StandardApiResponse<GarantiaPublicaResponseDto>> consultar(
            @PathVariable String folio, @RequestParam String telefono) {
        return ResponseEntityBuilder.ok(garantiaService.consultaPublica(folio, telefono), "garantía");
    }
}
