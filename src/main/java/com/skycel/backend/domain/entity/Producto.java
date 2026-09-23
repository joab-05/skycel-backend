package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Audited
// (codpro, codti) único: un artículo lleva el mismo código en todas las sucursales, pero una sola vez por sucursal.
// Antes solo lo validaba el código; dos altas simultáneas podían duplicarlo.
@Table(name = "producto",
        uniqueConstraints = @UniqueConstraint(name = "uk_producto_codpro_tienda", columnNames = {"codpro", "codti"}),
        indexes = @Index(name = "idx_producto_tienda_activo", columnList = "codti, activo"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idproducto")
    private Integer idproducto;

    @Column(name = "codpro", nullable = false, length = 25)
    private String codpro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idprodmaster", nullable = false)
    private ProductoMaster productoMaster;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idmagnitud", nullable = false, columnDefinition = "SMALLINT DEFAULT 1")
    private Magnitud magnitud;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcolor")
    private Color color;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproveedor")
    private Proveedor proveedor;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idseccion")
    private Seccion seccion;

    @Column(name = "stock", precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal stock;

    // Umbral de reposición: cuando stock <= stockMinimo el producto aparece en las alertas de bajo stock.
    // null o 0 = sin alerta. No aplica a servicios.
    @Column(name = "stock_minimo", precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal stockMinimo;

    @Column(name = "preciopro", nullable = false, precision = 12, scale = 4, columnDefinition = "DECIMAL(12,4) DEFAULT 0.0000")
    private BigDecimal preciopro;

    @Column(name = "preciopub", nullable = false, precision = 12, scale = 4, columnDefinition = "DECIMAL(12,4) DEFAULT 0.0000")
    private BigDecimal preciopub;

    @CreationTimestamp
    @Column(name = "fecha_ingreso", updatable = false)
    private LocalDateTime fechaIngreso;

    @UpdateTimestamp
    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_mod_id")
    private Usuario usuarioModificador;

    @Column(name = "rezagado", columnDefinition = "TINYINT DEFAULT 0")
    private Boolean rezagado;

    @Column(name = "publico", columnDefinition = "TINYINT DEFAULT 0")
    private Boolean publico;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    @Version
    @Column(name = "version")
    private Integer version;
}
