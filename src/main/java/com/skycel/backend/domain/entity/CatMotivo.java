package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cat_motivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatMotivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idmotivo")
    private Integer idmotivo;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "tipo_mov", nullable = false, columnDefinition = "TINYINT")
    private Byte tipoMov;

    @Column(name = "cat_sat", nullable = false, columnDefinition = "TINYINT")
    private Byte catSat;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;
}
