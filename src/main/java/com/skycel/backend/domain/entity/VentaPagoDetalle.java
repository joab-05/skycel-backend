package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Desglose de pagos de una venta (venta_pago_detalle). Se usa en las ventas con método Mixto:
 * una línea por cada método con el que se pagó, y opcionalmente el folio del voucher o el número
 * de rastreo de la transferencia.
 */
@Entity
@Table(name = "venta_pago_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VentaPagoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idpago_detalle")
    private Integer idpagoDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idventa", nullable = false)
    private Venta venta;

    /** 1 = Efectivo, 2 = Tarjeta, 3 = Transferencia, 5 = PayJoy */
    @Column(name = "metodo_pago", nullable = false, columnDefinition = "TINYINT")
    private Byte metodoPago;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "folio_operacion", length = 50)
    private String folioOperacion;
}
