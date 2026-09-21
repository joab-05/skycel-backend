package com.skycel.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/** Para que los equipos sepan si el servidor está disponible (el modo sin conexión lo consulta cada pocos segundos). */
@RestController
@RequestMapping("/api/publico")
@Tag(name = "Salud", description = "Disponibilidad del servidor")
public class PublicoSaludController {

    @Operation(summary = "¿El servidor responde?", description = "Sin sesión y sin datos: solo confirma que está en línea y su hora.")
    @GetMapping("/salud")
    public ResponseEntity<Map<String, Object>> salud() {
        return ResponseEntity.ok(Map.of("ok", true, "hora", LocalDateTime.now().toString()));
    }
}
