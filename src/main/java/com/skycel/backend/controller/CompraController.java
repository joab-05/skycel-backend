package com.skycel.backend.controller;

import com.skycel.backend.dto.compra.CompraRequestDto;
import com.skycel.backend.service.CompraService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Compras de mercancía a proveedor y su cuenta por pagar. Registrar una compra suma el stock recibido y deja el
 * saldo que la tienda le debe al proveedor; el mismo rol que hoy ajusta stock (ROOT/ADMIN/ENCARGADO_TIENDA) puede
 * registrarlas, cada quien en su propia sucursal salvo un administrador.
 */
@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
@Tag(name = "Compras", description = "Compras a proveedor y cuentas por pagar")
public class CompraController {

    private final CompraService compraService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> listar(
            @RequestParam(required = false) Integer codti,
            @RequestParam(defaultValue = "false") boolean soloActivas,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(compraService.listar(codti, soloActivas, userDetails.getUsername()), "compras");
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> resumen() {
        return ResponseEntityBuilder.ok(compraService.resumen(), "resumen");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> obtener(@PathVariable Integer id) {
        return ResponseEntityBuilder.ok(compraService.obtener(id), "compra");
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> crear(
            @RequestBody CompraRequestDto dto, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.created(compraService.crear(dto, userDetails.getUsername()), "compra");
    }

    @PostMapping("/{id}/pago")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> registrarPago(
            @PathVariable Integer id,
            @RequestParam BigDecimal monto,
            @RequestParam(defaultValue = "1") Byte metodoPago,
            @RequestParam(required = false) String notas,
            @Parameter(description = "Caja de la sucursal de la compra donde sale el efectivo. Opcional: si se omite se usa su caja principal")
            @RequestParam(required = false) Integer idCaja,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.updated(
                compraService.registrarPago(id, monto, metodoPago, notas, userDetails.getUsername(), idCaja),
                "compra");
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> historial(@PathVariable Integer id) {
        return ResponseEntityBuilder.ok(compraService.historialPagos(id), "pagos");
    }
}
