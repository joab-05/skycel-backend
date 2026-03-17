package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "magnitud")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Magnitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idmagnitud")
    private Short idmagnitud;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "abreviatura", nullable = false, length = 5)
    private String abreviatura;

    @Column(name = "criterio", nullable = false)
    private Short criterio;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;
}
