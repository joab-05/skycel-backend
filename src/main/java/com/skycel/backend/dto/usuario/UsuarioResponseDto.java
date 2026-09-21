package com.skycel.backend.dto.usuario;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de respuesta para GET /api/usuarios
 * Combina Usuario + Tienda + EmpleadoPerfil en un solo objeto plano.
 */
@Data
@Builder
public class UsuarioResponseDto {

    // ── Usuario ───────────────────────────────────────────────────────────────
    private Integer idusuario;
    private String  username;
    private String  nombreCompleto;
    private String  rol;           // nombre del enum: ROOT, ADMIN, ENCARGADO_TIENDA, VENDEDOR…
    private String  telefono;
    private String  email;
    private Boolean activo;
    /** Solo un TECNICO: es el técnico encargado (asigna las reparaciones). */
    private Boolean tecnicoEncargado;
    private LocalDateTime fechaAlta;

    // ── Tienda ────────────────────────────────────────────────────────────────
    private Integer codti;
    private String  nombreTienda;

    // ── EmpleadoPerfil (nullable si aún no tiene perfil) ──────────────────────
    private Integer    idempleado;
    private BigDecimal sueldoBase;
    private LocalDate  fechaIngreso;
    private BigDecimal limiteFondoInventario;
    private BigDecimal acumuladoFondoInventario;
}