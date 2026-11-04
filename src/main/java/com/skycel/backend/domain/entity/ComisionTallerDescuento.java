package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Lo que se le descuenta a un técnico de su comisión de un mes (una cuenta pendiente, algo que compró...). */
@Entity
@Table(name = "taller_comision_descuento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComisionTallerDescuento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddescuento")
    private Integer iddescuento;

    @Column(name = "anio", nullable = false)
    private Integer anio;

    @Column(name = "mes", nullable = false)
    private Integer mes;

    @Column(name = "idusuario", nullable = false)
    private Integer idusuario;

    @Column(name = "concepto", nullable = false, length = 150)
    private String concepto;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "creado_por", length = 100)
    private String creadoPor;

    @Column(name = "creado_el")
    private LocalDateTime creadoEl;

    @PrePersist
    void alCrear() {
        if (creadoEl == null) creadoEl = LocalDateTime.now();
    }
}
