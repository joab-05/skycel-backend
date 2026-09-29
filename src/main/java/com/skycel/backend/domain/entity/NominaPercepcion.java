package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "nomina_percepcion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NominaPercepcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idpercepcion")
    private Integer idpercepcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iddetalle", nullable = false)
    private NominaDetalle detalle;

    @Column(name = "concepto", nullable = false, length = 100)
    private String concepto;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;
}
