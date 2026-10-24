package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reclamo de garantía de un producto vendido, que puede viajar al proveedor.
 *
 * estado_actual: 1 Recibido en sucursal, 2 En tránsito a bodega, 3 Recibido en bodega, 4 En tránsito al proveedor,
 * 5 En el proveedor, 6 Reparado por el proveedor, 7 Cambio físico, 8 Rechazado, 9 Viaje de retorno,
 * 10 Listo para entrega, 11 Entregado.
 */
@Entity
@Audited
@Table(name = "garantia")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Garantia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idgarantia")
    private Integer idgarantia;

    /** La venta original. Es null cuando el producto se vendió con el sistema anterior ({@link #ventaAnterior}). */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idventa")
    private Venta venta;

    /**
     * El producto (en la tienda que lo vendió) al que pertenece el equipo o accesorio reclamado. Es null cuando el
     * producto se vendió con el sistema anterior: ya no existe en este inventario.
     */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproducto")
    private Producto producto;

    /** El artículo reclamado (equipos, que no tienen producto con código; en lo demás, el del producto). */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idprodmaster")
    private ProductoMaster productoMaster;

    /**
     * Garantía de vitrina: un equipo que aún no se vende y al que se le detectó una falla. No hay venta ni cliente; mientras
     * está fuera la unidad queda EN_GARANTIA (no cuenta como existencia) y vuelve a DISPONIBLE al entregarse.
     */
    @Builder.Default
    @Column(name = "vitrina", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private Boolean vitrina = false;

    // ── Ventas del sistema anterior ───────────────────────────────────────────

    /** true si el producto se vendió antes de este sistema: no hay venta ni producto aquí, se captura a mano. */
    @Builder.Default
    @Column(name = "venta_anterior", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private Boolean ventaAnterior = false;

    @Column(name = "nombre_producto_anterior", length = 255)
    private String nombreProductoAnterior;

    /** CELULAR (equipo) o ACCESORIO. */
    @Column(name = "tipo_producto_anterior", length = 20)
    private String tipoProductoAnterior;

    @Column(name = "fecha_compra_anterior")
    private LocalDate fechaCompraAnterior;

    @Column(name = "dias_garantia_anterior")
    private Integer diasGarantiaAnterior;

    /** Número de ticket o venta del sistema anterior, si el cliente lo trae (solo para rastrear). */
    @Column(name = "folio_venta_anterior", length = 40)
    private String folioVentaAnterior;

    /** Quien recibió el equipo e hizo la inspección inicial. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_recibe", nullable = false)
    private Usuario usuarioRecibe;

    /** Folio público para que el cliente consulte el avance (GAR-000001). */
    @Column(name = "folio_seguimiento", nullable = false, length = 20, unique = true)
    private String folioSeguimiento;

    @CreationTimestamp
    @Column(name = "fecha_ingreso", updatable = false)
    private LocalDateTime fechaIngreso;

    /** Notas de quien recibe: golpes, rayones, estado general. */
    @Column(name = "diagnostico_inicial", columnDefinition = "TEXT")
    private String diagnosticoInicial;

    @Column(name = "falla_reportada", nullable = false, columnDefinition = "TEXT")
    private String fallaReportada;

    @Column(name = "estado_actual", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Byte estadoActual;

    /** Fecha de ingreso + 30 días hábiles. */
    @Column(name = "fecha_limite_solucion")
    private LocalDate fechaLimiteSolucion;

    // ── Columnas que el diseño original no incluía ────────────────────────────

    /** Sucursal donde el cliente entregó el equipo (y adonde regresa). */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti_recepcion", nullable = false)
    private Tienda tienda;

    /** IMEI o serie del equipo reclamado (solo equipos; los accesorios se identifican por su producto). */
    @Column(name = "imei", length = 20)
    private String imei;

    /** A quién avisarle: el cliente de la venta o, si la venta no tenía cliente, quien lo entrega. */
    @Column(name = "nombre_contacto", nullable = false, length = 150)
    private String nombreContacto;

    @Column(name = "telefono_contacto", nullable = false, length = 20)
    private String telefonoContacto;

    /** Proveedor del producto, al que se manda a garantía. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproveedor")
    private Proveedor proveedor;

    /** En un cambio físico: el IMEI o serie del equipo nuevo que entregó el proveedor. */
    @Column(name = "imei_reemplazo", length = 20)
    private String imeiReemplazo;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Version
    @Column(name = "version")
    private Integer version;
}
