package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Audited
@Table(name = "venta", indexes = {
        @Index(name = "idx_venta_tienda_fecha", columnList = "codti, fecha_venta"),
        @Index(name = "idx_venta_fecha", columnList = "fecha_venta")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idventa")
    private Integer idventa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_vendedor", nullable = false)
    private Usuario usuarioVendedor;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcliente")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    @Column(name = "tipo_comprobante", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Byte tipoComprobante;

    @Column(name = "metodo_pago", nullable = false, columnDefinition = "TINYINT")
    private Byte metodoPago;

    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Byte estado;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "monto_abonado", precision = 12, scale = 2, columnDefinition = "DECIMAL(12,2) DEFAULT 0.00")
    private BigDecimal montoAbonado;

    /** Cuándo se hizo la venta. Una venta hecha sin conexión conserva la fecha en que ocurrió, no la de la sincronización. */
    @Column(name = "fecha_venta", updatable = false)
    private LocalDateTime fechaVenta;

    /**
     * Identificador único (UUID) que genera el equipo que hizo la venta sin conexión. Evita duplicarla si el envío se repite.
     */
    @Column(name = "clave_offline", length = 64, unique = true)
    private String claveOffline;

    /** Folio provisional del ticket impreso sin conexión (p. ej. OFF-ZOC1-000123): con él se localiza la venta. */
    @Column(name = "folio_local", length = 40)
    private String folioLocal;

    @PrePersist
    void alGuardar() {
        if (fechaVenta == null) fechaVenta = LocalDateTime.now();
    }

    @Column(name = "observaciones", length = 255)
    private String observaciones;
}
