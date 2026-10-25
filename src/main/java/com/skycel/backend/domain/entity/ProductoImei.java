package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un equipo (celular o tablet): una unidad física identificada por su IMEI (o, en tablets, su número de serie). Cada unidad
 * apunta directo al artículo al que pertenece ({@link ProductoMaster}: categorías, nombre y color) y a la sucursal donde está;
 * no hay un registro por modelo ni un código de producto. Sus existencias son simplemente las unidades DISPONIBLES.
 *
 * <p>{@code producto} es el registro por modelo que usaba el sistema antes de este cambio: solo lo conservan las unidades
 * anteriores, para no perder la referencia desde ventas, traspasos o garantías viejas. Las unidades nuevas lo dejan vacío.
 */
@Entity
@Audited
@Table(name = "producto_imei", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"imei"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoImei {

    public static final String IMEI = "IMEI";
    public static final String SERIE = "SERIE";

    public static final String DISPONIBLE = "DISPONIBLE";
    public static final String VENDIDO = "VENDIDO";
    public static final String EN_GARANTIA = "EN_GARANTIA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** El registro por modelo de antes (solo unidades anteriores); null en las nuevas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproducto")
    private Producto producto;

    /** El artículo al que pertenece la unidad. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idprodmaster")
    private ProductoMaster productoMaster;

    /** La sucursal donde está la unidad ahora (cambia con los traspasos). */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti")
    private Tienda tienda;

    @Column(name = "imei", nullable = false, length = 20)
    private String imei;

    /** IMEI (15 dígitos) o SERIE (número de serie). Un celular siempre se identifica por su IMEI; una tablet, por cualquiera de los dos. */
    @Column(name = "tipo_identificador", length = 10)
    @Builder.Default
    private String tipoIdentificador = IMEI;

    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private String estado = DISPONIBLE; // DISPONIBLE, VENDIDO, TRASPASADO (en camino), DEVUELTO, DEBAJA, EN_GARANTIA

    /** Precio de venta de ESTA unidad. */
    @Column(name = "precio_venta_override", precision = 12, scale = 2)
    private BigDecimal precioVenta;

    /** Condición de ESTA unidad: NUEVO, SEMINUEVO o REACONDICIONADO. */
    @Column(name = "condicion", nullable = false, length = 20, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'NUEVO'")
    @Builder.Default
    private String condicion = "NUEVO";

    /** Cuánto costó ESTA unidad. */
    @Column(name = "costo_unitario", precision = 12, scale = 2)
    private BigDecimal costo;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproveedor")
    private Proveedor proveedor;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idseccion")
    private Seccion seccion;

    /** Marcada a mano como rezagada (que no se vende), aunque lleve poco tiempo en existencia. */
    @Column(name = "rezagado", columnDefinition = "TINYINT DEFAULT 0")
    @Builder.Default
    private Boolean rezagado = false;

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    @UpdateTimestamp
    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
