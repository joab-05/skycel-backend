package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Renglón de una orden: un servicio (mano de obra) o una refacción (accesorio del inventario que se usa
 * en la reparación). Al entregar se vuelven líneas de la venta.
 */
@Entity
@Table(name = "orden_servicio_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenServicioDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddetalle")
    private Integer iddetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idorden", nullable = false)
    private OrdenServicio orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idprodmaster", nullable = false)
    private ProductoMaster productoMaster;

    /** Código del producto en la tienda de la orden. */
    @Column(name = "codpro", nullable = false, length = 25)
    private String codpro;

    @Column(name = "cantidad", nullable = false)
    private Short cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    /** Regalo (precio $0 autorizado), p. ej. "incluye mica gratis". */
    @Column(name = "es_regalo", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean esRegalo;
}
