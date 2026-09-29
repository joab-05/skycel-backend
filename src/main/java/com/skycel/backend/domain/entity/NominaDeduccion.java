package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "nomina_deduccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NominaDeduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddeduccion")
    private Integer iddeduccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iddetalle", nullable = false)
    private NominaDetalle detalle;

    @Column(name = "concepto", nullable = false, length = 100)
    private String concepto;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    // Cuando esta deducción es el abono a un préstamo del empleado; null si es una deducción libre.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idprestamo")
    private EmpleadoPrestamo prestamo;
}
