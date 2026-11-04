package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** El porcentaje de la ganancia neta del taller que gana un técnico cada mes. */
@Entity
@Table(name = "taller_comision_tecnico")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComisionTallerTecnico {

    @Id
    @Column(name = "idusuario")
    private Integer idusuario;

    @Column(name = "porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;
}
