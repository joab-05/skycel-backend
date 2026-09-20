package com.skycel.backend.controller;

import com.skycel.backend.dto.traspaso.*;
import com.skycel.backend.service.TraspasoService;
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

import java.util.List;

@RestController
@RequestMapping("/api/traspasos")
@RequiredArgsConstructor
@Tag(name = "Traspasos", description = "Envíos y solicitudes de mercancía entre tiendas")
public class TraspasoController {

    private final TraspasoService traspasoService;

    @Operation(summary = "Crear un envío",
            description = "La tienda origen manda mercancía a otra. El stock sale del origen y los equipos quedan en tránsito " +
                    "hasta que la tienda destino confirme la recepción.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/envios")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<TraspasoResponseDto>> crearEnvio(
            @Valid @RequestBody EnvioRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(traspasoService.crearEnvio(request, user.getUsername()), "envío");
    }

    @Operation(summary = "Crear una solicitud de reabastecimiento",
            description = "Una tienda le pide mercancía a otra, que la acepta (y genera el envío) o la rechaza.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/solicitudes")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<TraspasoResponseDto>> crearSolicitud(
            @Valid @RequestBody SolicitudRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(traspasoService.crearSolicitud(request, user.getUsername()), "solicitud");
    }

    @Operation(summary = "Confirmar la recepción de un envío",
            description = "La tienda destino confirma que llegó la mercancía: el stock y las unidades pasan a su inventario. " +
                    "Sin cuerpo se recibe completo; si algo no llegó, se indica en 'faltantes' (con un comentario obligatorio) " +
                    "y queda pendiente de resolver.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/recibir")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<TraspasoResponseDto>> recibir(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) RecepcionRequestDto request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(traspasoService.recibir(id, request, user.getUsername()), "envío");
    }

    @Operation(summary = "Faltantes por resolver",
            description = "Mercancía de envíos recibidos que no llegó y sigue pendiente. El administrador ve todas las tiendas " +
                    "(o filtra por una); el encargado, solo los de la suya.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/faltantes")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<List<FaltanteResponseDto>>> faltantes(
            @RequestParam(required = false) Integer codti, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(traspasoService.listarFaltantes(codti, user.getUsername()), "faltantes");
    }

    @Operation(summary = "Resolver un faltante",
            description = "accion: RECIBIDO_TARDE (llegó; lo confirma la tienda destino), REINTEGRADO_ORIGEN (nunca salió; lo confirma " +
                    "el origen) o BAJA (solo ROOT/ADMIN, con nota). Se puede resolver por partes.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/faltantes/{iddetalle}/resolver")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<FaltanteResponseDto>> resolverFaltante(
            @PathVariable Integer iddetalle,
            @Valid @RequestBody ResolverFaltanteRequestDto request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(traspasoService.resolverFaltante(iddetalle, request, user.getUsername()), "faltante");
    }

    @Operation(summary = "Marcar una solicitud como leída")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/leer")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<TraspasoResponseDto>> leer(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(traspasoService.leer(id, user.getUsername()), "solicitud");
    }

    @Operation(summary = "Aceptar una solicitud y enviar lo pedido",
            description = "Devuelve el envío generado. Para los equipos pedidos hay que indicar qué IMEI se mandan.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/aceptar")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<TraspasoResponseDto>> aceptar(
            @PathVariable Integer id,
            @RequestBody(required = false) AceptarSolicitudRequestDto request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(traspasoService.aceptar(id, request, user.getUsername()), "envío");
    }

    @Operation(summary = "Rechazar una solicitud")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<TraspasoResponseDto>> rechazar(
            @PathVariable Integer id,
            @Valid @RequestBody RechazoRequestDto request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(traspasoService.rechazar(id, request, user.getUsername()), "solicitud");
    }

    @Operation(summary = "Anular un envío no recibido o una solicitud sin resolver",
            description = "En un envío, el stock y las unidades regresan al origen.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<TraspasoResponseDto>> anular(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(traspasoService.anular(id, user.getUsername()), "traspaso");
    }

    @Operation(summary = "Consultar un traspaso")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<TraspasoResponseDto>> obtener(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(traspasoService.obtener(id, user.getUsername()), "traspaso");
    }

    @Operation(summary = "Traspasos que entran o salen de una tienda",
            description = "Filtros opcionales: tipo (1 envío, 2 solicitud) y estado (1 a 5). Por defecto no incluye los anulados.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<List<TraspasoResponseDto>>> listar(
            @PathVariable Integer codti,
            @RequestParam(required = false) Byte tipo,
            @RequestParam(required = false) Byte estado,
            @RequestParam(defaultValue = "false") boolean incluirAnulados,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(
                traspasoService.listar(codti, tipo, estado, incluirAnulados, user.getUsername()), "traspasos");
    }

    @Operation(summary = "Pendientes de una tienda",
            description = "Envíos por recibir y solicitudes por leer o resolver dirigidos a la tienda.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}/pendientes")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<List<TraspasoResponseDto>>> pendientes(
            @PathVariable Integer codti, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(traspasoService.pendientes(codti, user.getUsername()), "pendientes");
    }
}
