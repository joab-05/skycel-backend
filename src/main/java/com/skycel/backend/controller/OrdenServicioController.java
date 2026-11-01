package com.skycel.backend.controller;

import com.skycel.backend.dto.ordenservicio.*;
import com.skycel.backend.service.OrdenServicioService;
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
@RequestMapping("/api/ordenes-servicio")
@RequiredArgsConstructor
@Tag(name = "Órdenes de servicio", description = "Taller: recepción de equipos, reparación, anticipos y entrega")
public class OrdenServicioController {

    /** El trabajo del taller (estados, servicios, observaciones): administradores y técnicos. */

    private final OrdenServicioService ordenService;

    @Operation(summary = "Recibir un equipo (crear una orden de servicio)",
            description = "Opcionalmente con técnico, renglones (servicios y refacciones) y un anticipo.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("@permisos.tiene('ORDEN_RECIBIR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> crear(
            @Valid @RequestBody OrdenServicioRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(ordenService.crear(request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Consultar una orden")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> obtener(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.obtener(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Consultar una orden por su folio (OS-000001)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/folio/{folio}")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> obtenerPorFolio(
            @PathVariable String folio, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.obtenerPorFolio(folio, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Órdenes de una tienda",
            description = "Filtros opcionales: estado (1 Recibida, 2 En reparación, 3 Lista, 4 Entregada, 5 Cancelada) y técnico.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/tienda/{codti}")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<List<OrdenServicioResponseDto>>> listar(
            @PathVariable Integer codti,
            @RequestParam(required = false) Byte estado,
            @RequestParam(required = false) Integer idTecnico,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.listar(codti, estado, idTecnico, user.getUsername()), "órdenes de servicio");
    }

    @Operation(summary = "Mis órdenes abiertas (para un técnico)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/mis-ordenes")
    @PreAuthorize("hasRole('TECNICO')")
    public ResponseEntity<StandardApiResponse<List<OrdenServicioResponseDto>>> misOrdenes(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.misOrdenes(user.getUsername()), "órdenes de servicio");
    }

    @Operation(summary = "Actualizar los datos de una orden abierta (incluye el diagnóstico)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PatchMapping("/{id}")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> actualizar(
            @PathVariable Integer id, @Valid @RequestBody OrdenActualizarDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.actualizar(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Asignar (o cambiar) el técnico")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/tecnico")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> asignarTecnico(
            @PathVariable Integer id, @Valid @RequestBody AsignarTecnicoDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.asignarTecnico(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "El taller: órdenes abiertas de todas las sucursales",
            description = "Un administrador y el técnico encargado ven todas; un técnico, las que nadie ha tomado y las suyas.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/taller")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<List<OrdenServicioResponseDto>>> taller(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.taller(user.getUsername()), "órdenes del taller");
    }

    @Operation(summary = "Tomar una orden (recibir el equipo en el taller)",
            description = "Un técnico toma una orden que nadie ha tomado: queda asignada a él y en la bitácora consta la recolección. " +
                    "Cualquier técnico puede hacerlo (p. ej. si el técnico encargado no está).")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/tomar")
    @PreAuthorize("hasRole('TECNICO')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> tomar(@PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.tomar(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Agregar una observación a la orden",
            description = "Queda en la bitácora sin cambiar el estado. Solo un administrador o un técnico.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/notas")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> agregarNota(
            @PathVariable Integer id, @Valid @RequestBody ComentarioRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.agregarNota(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Agregar un servicio o una refacción a la orden")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/lineas")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> agregarLinea(
            @PathVariable Integer id, @Valid @RequestBody OrdenLineaRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.agregarLinea(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Agregar una pieza que el técnico compró para la orden y no estaba en el inventario",
            description = "Da de alta la pieza en el inventario del taller (categoría Refacciones) con la cantidad comprada y la agrega a la orden.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/piezas-compradas")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> agregarPiezaComprada(
            @PathVariable Integer id, @Valid @RequestBody PiezaCompradaRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.agregarPiezaComprada(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Quitar un renglón de la orden")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{id}/lineas/{iddetalle}")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> eliminarLinea(
            @PathVariable Integer id, @PathVariable Integer iddetalle, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.eliminarLinea(id, iddetalle, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Iniciar la reparación", description = "Requiere un técnico asignado.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/iniciar")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> iniciar(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.iniciar(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Marcar la orden como lista para entrega",
            description = "Exige al menos un renglón y stock de las refacciones.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/lista")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> lista(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.marcarLista(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Confirmar que el equipo regresó del taller a la sucursal",
            description = "Hasta que regrese no se avisa al cliente que ya puede recogerlo ni se cobra. Lo confirma el personal de la sucursal que recibió el equipo.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/regreso")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> regreso(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.regresoATienda(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Cotizar: dejar la orden esperando que el cliente autorice el total")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/autorizacion/solicitar")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> solicitarAutorizacion(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.solicitarAutorizacion(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Registrar la respuesta del cliente a la cotización (autorizó o no, y por qué medio)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/autorizacion")
    @PreAuthorize("@permisos.tiene('ORDEN_RECIBIR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> registrarAutorizacion(
            @PathVariable Integer id, @Valid @RequestBody AutorizacionRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.registrarAutorizacion(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Mensaje de WhatsApp para el cliente (ya escrito) y registro del aviso",
            description = "tipo: RECIBIDA, COTIZACION, PRECIO_CAMBIO, LISTA, RECORDATORIO o ENTREGADA; sin tipo se elige el que corresponde al momento.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/aviso-whatsapp")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<java.util.Map<String, Object>>> avisoWhatsapp(
            @PathVariable Integer id, @RequestParam(required = false) String tipo, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.avisoWhatsapp(id, tipo, user.getUsername()), "aviso");
    }

    @Operation(summary = "Detener la reparación mientras llega una refacción", description = "El comentario dice qué se espera. El cliente lo ve como «esperando una refacción».")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/espera")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> esperarRefaccion(
            @PathVariable Integer id, @Valid @RequestBody ComentarioRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.esperarRefaccion(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Llegó la refacción: la reparación continúa")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/espera/terminar")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> reanudarEspera(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.reanudarEspera(id, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Equipos listos que siguen en la tienda", description = "Los que llevan más días sin recoger primero. Sin codti: todas las sucursales (administrador).")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/por-recoger")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<List<OrdenServicioResponseDto>>> porRecoger(
            @RequestParam(required = false) Integer codti, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.porRecoger(codti, user.getUsername()), "órdenes por recoger");
    }

    @Operation(summary = "Reporte de tiempos del taller", description = "Del ingreso a lista y a entrega, cumplimiento de la fecha prometida, retrasadas y por técnico. " +
            "Por defecto los últimos 30 días.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/reporte")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<java.util.Map<String, Object>>> reporte(
            @RequestParam(required = false) Integer codti,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.reporte(codti, desde, hasta, user.getUsername()), "reporte de tiempos");
    }

    @Operation(summary = "Fotos de la orden (sin la imagen)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}/fotos")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<List<java.util.Map<String, Object>>>> fotos(
            @PathVariable Integer id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.fotos(id, user.getUsername()), "fotos");
    }

    @Operation(summary = "Agregar una foto del equipo", description = "El cuerpo es la imagen JPEG o PNG (máximo 1.5 MB, hasta 8 por orden); la descripción va en ?descripcion=.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping(value = "/{id}/fotos", consumes = {"image/jpeg", "image/png", "application/octet-stream"})
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<List<java.util.Map<String, Object>>>> agregarFoto(
            @PathVariable Integer id, @RequestParam(required = false) String descripcion, @RequestBody byte[] imagen,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.created(ordenService.agregarFoto(id, imagen, descripcion, user.getUsername()), "foto");
    }

    @Operation(summary = "La imagen de una foto")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}/fotos/{idfoto}")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<byte[]> foto(@PathVariable Integer id, @PathVariable Integer idfoto, @AuthenticationPrincipal UserDetails user) {
        var f = ordenService.foto(id, idfoto, user.getUsername());
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(f.getTipo()))
                .header("Cache-Control", "private, max-age=3600").body(f.getContenido());
    }

    @Operation(summary = "Borrar una foto de la orden")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{id}/fotos/{idfoto}")
    @PreAuthorize("@permisos.tiene('ORDEN_CONSULTAR')")
    public ResponseEntity<StandardApiResponse<List<java.util.Map<String, Object>>>> eliminarFoto(
            @PathVariable Integer id, @PathVariable Integer idfoto, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.ok(ordenService.eliminarFoto(id, idfoto, user.getUsername()), "fotos");
    }

    @Operation(summary = "Reabrir una orden lista (vuelve a reparación)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/reabrir")
    @PreAuthorize("@permisos.tiene('TALLER_TRABAJAR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> reabrir(
            @PathVariable Integer id, @Valid @RequestBody ComentarioRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.reabrir(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Registrar un anticipo", description = "Solo el efectivo entra a la caja.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/anticipos")
    @PreAuthorize("@permisos.tiene('ORDEN_RECIBIR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> registrarAnticipo(
            @PathVariable Integer id, @Valid @RequestBody AnticipoRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.registrarAnticipo(id, request, user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Entregar el equipo y cobrar el saldo",
            description = "Genera la venta de la orden. El anticipo ya cobrado no vuelve a entrar a la caja.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/entregar")
    @PreAuthorize("@permisos.tiene('ORDEN_RECIBIR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> entregar(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) EntregaRequestDto request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(
                ordenService.entregar(id, request != null ? request : new EntregaRequestDto(), user.getUsername()), "orden de servicio");
    }

    @Operation(summary = "Cancelar la orden", description = "Opcionalmente devuelve los anticipos en efectivo.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("@permisos.tiene('ORDEN_RECIBIR')")
    public ResponseEntity<StandardApiResponse<OrdenServicioResponseDto>> cancelar(
            @PathVariable Integer id, @Valid @RequestBody CancelarOrdenRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntityBuilder.updated(ordenService.cancelar(id, request, user.getUsername()), "orden de servicio");
    }
}
