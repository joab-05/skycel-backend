package com.skycel.backend.dto.usuario;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para POST /api/usuarios — crear nuevo usuario + perfil de empleado
 */
@Data
public class UsuarioCreateDto {

    // ── Usuario ───────────────────────────────────────────────────────────────
    private String username;        // obligatorio, único
    private String password;        // obligatorio — se hashea con BCrypt en el service
    private String nombreCompleto;  // obligatorio
    private Integer codti;          // ya no se usa: el encargado se designa en la tienda; se ignora si llega
    private Boolean tecnicoEncargado;   // solo si el rol es TECNICO
    private String rol;             // obligatorio — ROOT, ADMIN, ENCARGADO_TIENDA, VENDEDOR
    private String telefono;        // opcional
    private String email;           // opcional

    // ── EmpleadoPerfil ────────────────────────────────────────────────────────
    private BigDecimal sueldoBase;             // opcional, default 0
    private LocalDate  fechaIngreso;           // opcional, default hoy
    private BigDecimal limiteFondoInventario;  // opcional, default 0
}