package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ajuste manual al saldo de descansos de un empleado (positivo o negativo): vacaciones al cumplir un año,
 * un permiso por salud, una corrección, etc. — lo cotidiano del negocio que una fórmula fija no cubre.
 */
@Entity
@Table(name = "empleado_descanso_ajuste")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpleadoDescansoAjuste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idajuste")
    private Integer idajuste;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idempleado", nullable = false)
    private EmpleadoPerfil empleado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    // Positivo suma días al saldo (vacaciones, permiso), negativo los resta (corrección).
    @Column(name = "dias", nullable = false)
    private Integer dias;

    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_registro")
    private Usuario registradoPor;

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;
}
