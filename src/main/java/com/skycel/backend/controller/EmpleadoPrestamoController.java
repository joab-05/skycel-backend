package com.skycel.backend.controller;

import com.skycel.backend.service.EmpleadoPrestamoService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
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

/** Préstamos/adelantos a empleados, descontados desde la nómina. Solo ROOT/ADMIN. */
@RestController
@RequestMapping("/api/prestamos-empleado")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROOT','ADMIN')")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Préstamos a empleados", description = "Adelantos de dinero, con saldo que se abona desde la nómina")
public class EmpleadoPrestamoController {

    private final EmpleadoPrestamoService service;

    @PostMapping
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> otorgar(
            @RequestParam Integer idempleado, @RequestParam BigDecimal monto, @RequestParam Integer idCaja,
            @RequestParam(required = false) String observaciones,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntityBuilder.ok(service.otorgar(idempleado, monto, idCaja, observaciones, userDetails.getUsername()), "prestamo");
    }

    @GetMapping("/empleado/{idempleado}")
    public ResponseEntity<StandardApiResponse<List<Map<String, Object>>>> listarPorEmpleado(@PathVariable Integer idempleado) {
        return ResponseEntityBuilder.ok(service.listarPorEmpleado(idempleado), "prestamos");
    }
}
