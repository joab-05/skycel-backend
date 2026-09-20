package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Un producto que se devuelve: el renglón de la venta original, cuánto y en qué condición regresa. */
@Entity
@Table(name = "devolucion_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DevolucionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddetalle")
    private Integer iddetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iddevolucion", nullable = false)
    private Devolucion devolucion;

    /** El renglón de la venta original. */
    @Column(name = "iddetalle_venta", nullable = false)
    private Integer iddetalleVenta;

    @Column(name = "codpro", length = 25)
    private String codpro;

    @Column(name = "nombre_producto", length = 255)
    private String nombreProducto;

    /** Equipos: la unidad que se devuelve. */
    @Column(name = "imei", length = 20)
    private String imei;

    @Column(name = "cantidad", nullable = false)
    private Short cantidad;

    /** Lo que se pagó por unidad en la venta. */
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    /** true = regresa al inventario para venderse; false = viene dañado y se da de baja. */
    @Column(name = "reingresa", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean reingresa;
}
