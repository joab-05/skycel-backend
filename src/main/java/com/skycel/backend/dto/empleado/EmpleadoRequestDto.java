package com.skycel.backend.dto.empleado;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmpleadoRequestDto {

    // ── Datos del Usuario ─────────────────────────────────────────────────────
    @NotBlank(message = "El username es obligatorio")
    private String username;

    /** Solo requerido en creación. En actualización puede ser null (no cambia). */
    private String password;

    @NotBlank(message = "El nombre completo es obligatorio")
    private String nombreCompleto;

    @NotNull(message = "La tienda es obligatoria")
    private Integer codti;

    private String telefono;
    private String email;

    /**
     * Rol del usuario: 0=ROOT, 1=ADMIN, 2=ENCARGADO_TIENDA, 3=VENDEDOR
     */
    @NotNull(message = "El rol es obligatorio")
    private Integer rol;

    // ── Datos del Perfil de Empleado ──────────────────────────────────────────
    @NotNull(message = "El sueldo base es obligatorio")
    @Positive(message = "El sueldo debe ser positivo")
    private BigDecimal sueldoBase;

    @NotNull(message = "La fecha de ingreso es obligatoria")
    private LocalDate fechaIngreso;

    private BigDecimal limiteFondoInventario;
}