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
@Table(name = "venta")
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

    @CreationTimestamp
    @Column(name = "fecha_venta", updatable = false)
    private LocalDateTime fechaVenta;

    @Column(name = "observaciones", length = 255)
    private String observaciones;
}
