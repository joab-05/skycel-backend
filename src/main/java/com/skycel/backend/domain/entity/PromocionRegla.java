package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promocion_regla")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionRegla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idpromocion")
    private Integer idpromocion;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "tipo_alcance", nullable = false, columnDefinition = "TINYINT")
    private Byte tipoAlcance;

    @Column(name = "idcat")
    private Short idcat;

    @Column(name = "idprodmaster")
    private Integer idprodmaster;

    @Column(name = "codti")
    private Integer codti;

    @Column(name = "es_exclusion", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean esExclusion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @Column(name = "factor_margen", precision = 5, scale = 2)
    private BigDecimal factorMargen;

    @Column(name = "descuento_fijo", precision = 12, scale = 2)
    private BigDecimal descuentoFijo;

    @Column(name = "prioridad")
    private Integer prioridad;

    @Column(name = "activa", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean activa;
}
