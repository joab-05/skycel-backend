package com.skycel.backend.dto.usuario;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para PUT /api/usuarios/{id} — editar usuario existente
 * El password es opcional: si viene vacío/null no se modifica.
 */
@Data
public class UsuarioUpdateDto {

    // ── Usuario ───────────────────────────────────────────────────────────────
    private String  nombreCompleto;
    private String  password;       // null o vacío = no cambia la contraseña
    private Integer codti;          // cambiar de sucursal
    private String  rol;            // cambiar rol
    private String  telefono;
    private String  email;
    private Boolean activo;         // activar / desactivar
    private Boolean tecnicoEncargado; // solo aplica al rol TECNICO

    // ── EmpleadoPerfil ────────────────────────────────────────────────────────
    private BigDecimal sueldoBase;
    private LocalDate  fechaIngreso;
    private BigDecimal limiteFondoInventario;
}