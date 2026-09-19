package com.skycel.backend.dto.empleado;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class EmpleadoResponseDto {

    // ── Usuario ───────────────────────────────────────────────────────────────
    private Integer idusuario;
    private String  username;
    private String  nombreCompleto;
    private Integer codti;
    private String  nombreTienda;
    private String  telefono;
    private String  email;
    private String  rol;
    private Boolean activo;
    private LocalDateTime fechaAlta;

    // ── Perfil de Empleado ────────────────────────────────────────────────────
    private Integer   idempleado;
    private BigDecimal sueldoBase;
    private LocalDate  fechaIngreso;
    private BigDecimal limiteFondoInventario;
    private BigDecimal acumuladoFondoInventario;
}