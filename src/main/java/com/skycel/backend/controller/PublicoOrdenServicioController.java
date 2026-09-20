package com.skycel.backend.controller;

import com.skycel.backend.dto.ordenservicio.OrdenServicioPublicaResponseDto;
import com.skycel.backend.service.OrdenServicioService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Consulta que hace el cliente de su orden de servicio, sin iniciar sesión (ruta abierta en SecurityConfig). Exige el
 * folio y los últimos 4 dígitos del teléfono del cliente; solo devuelve información de seguimiento. Igual que en las
 * garantías, cualquier falla da la misma respuesta 404 para que nadie pueda ir descubriendo folios.
 */
@RestController
@RequestMapping("/api/publico/ordenes-servicio")
@RequiredArgsConstructor
@Tag(name = "Consulta pública", description = "Seguimiento de una orden de servicio o garantía por el cliente, sin iniciar sesión")
public class PublicoOrdenServicioController {

    private final OrdenServicioService ordenService;

    @Operation(summary = "Seguimiento de una orden de servicio",
            description = "Folio (OS-000001) y últimos 4 dígitos del teléfono del cliente.")
    @GetMapping("/{folio}")
    public ResponseEntity<StandardApiResponse<OrdenServicioPublicaResponseDto>> consultar(
            @PathVariable String folio, @RequestParam String telefono) {
        return ResponseEntityBuilder.ok(ordenService.consultaPublica(folio, telefono), "orden de servicio");
    }
}
