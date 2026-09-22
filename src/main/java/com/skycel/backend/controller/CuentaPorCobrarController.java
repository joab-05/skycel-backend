package com.skycel.backend.controller;

import com.skycel.backend.dto.cpc.CuentaPorCobrarRequestDto;
import com.skycel.backend.service.CuentaPorCobrarService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cuentas-por-cobrar")
@RequiredArgsConstructor
public class CuentaPorCobrarController {

    private final CuentaPorCobrarService cpcService;
    private final com.skycel.backend.repository.UsuarioRepository usuarioRepository;

    /** GET /api/cuentas-por-cobrar — todas */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> listar(
            @RequestParam(defaultValue = "false") boolean soloActivas) {
        var lista = soloActivas ? cpcService.listarActivas() : cpcService.listarTodas();
        return ResponseEntityBuilder.ok(lista, "cuentas-por-cobrar");
    }

    /** GET /api/cuentas-por-cobrar/resumen */
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> resumen() {
        return ResponseEntityBuilder.ok(cpcService.resumen(), "resumen");
    }

    /** POST /api/cuentas-por-cobrar */
    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> crear(
            @RequestBody CuentaPorCobrarRequestDto dto) {
        return ResponseEntityBuilder.created(cpcService.crear(dto), "cuenta-por-cobrar");
    }

    /** POST /api/cuentas-por-cobrar/{id}/pago */
    @PostMapping("/{id}/pago")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> registrarPago(
            @PathVariable Integer id,
            @RequestParam BigDecimal monto,
            @RequestParam(defaultValue = "1") Byte metodoPago,
            @RequestParam(required = false) String notas,
            @Parameter(description = "Caja donde entra el efectivo. Opcional: si se omite se usa la caja principal de la tienda del usuario")
            @RequestParam(required = false) Integer idCaja,
            @Parameter(description = "Clave única (UUID) de un abono hecho sin conexión; reenviarla no duplica el pago")
            @RequestParam(required = false) String claveOffline,
            @Parameter(description = "Momento real del abono (solo sin conexión)")
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime fechaPago,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Obtener el ID del usuario autenticado
        Integer idusuario = usuarioRepository
                .findByUsername(userDetails.getUsername())
                .map(u -> u.getIdusuario())
                .orElseThrow();

        return ResponseEntityBuilder.updated(
                cpcService.registrarPago(id, monto, metodoPago, notas, idusuario, idCaja, claveOffline, fechaPago),
                "cuenta-por-cobrar");
    }

    /** GET /api/cuentas-por-cobrar/{id}/historial */
    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> historial(
            @PathVariable Integer id) {
        return ResponseEntityBuilder.ok(cpcService.historialPagos(id), "pagos");
    }
}