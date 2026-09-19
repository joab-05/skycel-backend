package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Renglón de un traspaso. Un accesorio es un renglón con su cantidad; un equipo (celular o tablet)
 * es un renglón por unidad (cantidad 1) con su IMEI o número de serie.
 * En un envío, codpro es el código del producto en la tienda origen; en una solicitud, en la tienda
 * destino (la que surte).
 */
@Entity
@Table(name = "traspaso_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TraspasoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddetalle")
    private Integer iddetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idtraspaso", nullable = false)
    private Traspaso traspaso;

    @Column(name = "codpro", nullable = false, length = 25)
    private String codpro;

    @Column(name = "cantidad", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    /** Solo equipos, en un envío: la unidad que viaja. */
    @Column(name = "imei", length = 20)
    private String imei;
}
