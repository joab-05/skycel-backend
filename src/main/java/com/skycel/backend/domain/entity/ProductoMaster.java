package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "producto_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idprodmaster")
    private Integer idprodmaster;

    @Column(name = "nombre_base", nullable = false, length = 255)
    private String nombreBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcat", nullable = false)
    private Categoria categoria;

    @Column(name = "especificaciones", columnDefinition = "TEXT")
    private String especificaciones;

    @Column(name = "nota_adicional", length = 500)
    private String notaAdicional;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    @Version
    @Column(name = "version")
    private Integer version;
}
