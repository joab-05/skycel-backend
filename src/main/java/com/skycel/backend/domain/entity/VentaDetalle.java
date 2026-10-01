package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;

@Entity
@Audited
@Table(name = "venta_detalle", indexes = @Index(name = "idx_venta_detalle_codpro", columnList = "codpro"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddetalle_venta")
    private Integer iddetalleVenta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idventa", nullable = false)
    private Venta venta;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idprodmaster", nullable = false)
    private ProductoMaster productoMaster;

    @Column(name = "codpro", length = 25)
    private String codpro;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idpromocion")
    private PromocionRegla promocion;

    @Column(name = "cantidad", nullable = false, columnDefinition = "SMALLINT DEFAULT 1")
    private Short cantidad;

    @Column(name = "precio_unitario_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitarioBase;

    @Column(name = "precio_unitario_final", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitarioFinal;

    @Column(name = "costo_unitario_compra", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoUnitarioCompra;

    @Column(name = "es_regalo", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean esRegalo;

    @Column(name = "imei", length = 20)
    private String imei;

    /** Solo en una línea de una venta a crédito PayJoy: lo que el cliente dejó de enganche por ESTE equipo
     *  (puede variar línea por línea si el carrito trae varios financiados). Null en cualquier otra línea. */
    @Column(name = "enganche_payjoy", precision = 12, scale = 2)
    private BigDecimal enganchePayjoy;

    /** Plazo en semanas que PayJoy le dio al cliente para liquidar el resto de ESTE equipo. Null si no es PayJoy. */
    @Column(name = "plazo_semanas_payjoy")
    private Integer plazoSemanasPayjoy;

    /** true cuando el precio cobrado no coincidió con el del sistema (venta sin conexión, aceptada ya ocurrida).
     *  No comisiona: de lo contrario se incentivaría pedir precios distintos para ganar más comisión. */
    @Column(name = "precio_autorizado", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean precioAutorizado;
}