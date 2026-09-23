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
@Table(name = "traspaso_detalle", indexes = @Index(name = "idx_traspaso_detalle_codpro", columnList = "codpro"))
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

    // ── Recepción parcial ─────────────────────────────────────────────────────
    // Lo que no llegó (cantidad - cantidadRecibida) se resuelve después: llegó tarde (suma a lo recibido), regresó al origen
    // o se dio de baja. Pendiente = cantidad - recibida - baja - reintegrada.

    /** Lo que efectivamente llegó. Null mientras el envío no se ha recibido. */
    @Column(name = "cantidad_recibida", precision = 10, scale = 2)
    private BigDecimal cantidadRecibida;

    /** De lo que faltó: lo que se dio de baja (extravío o daño). */
    @Column(name = "cantidad_baja", nullable = false, precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) NOT NULL DEFAULT 0.00")
    @Builder.Default
    private BigDecimal cantidadBaja = BigDecimal.ZERO;

    /** De lo que faltó: lo que se confirmó que nunca salió y regresó al inventario del origen. */
    @Column(name = "cantidad_reintegrada", nullable = false, precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) NOT NULL DEFAULT 0.00")
    @Builder.Default
    private BigDecimal cantidadReintegrada = BigDecimal.ZERO;
}
