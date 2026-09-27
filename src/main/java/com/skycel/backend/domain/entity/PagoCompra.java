package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un pago hecho a un proveedor contra una compra (abono a la cuenta por pagar). */
@Entity
@Table(name = "pago_compra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PagoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idpago")
    private Integer idpago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcompra", nullable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario", nullable = false)
    private Usuario usuario;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** 1=Efectivo 2=Tarjeta 3=Transferencia 4=PayJoy (mismo catálogo que un abono de cliente). */
    @Column(name = "metodo_pago", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Byte metodoPago = 1;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "fecha_pago", updatable = false)
    private LocalDateTime fechaPago;

    @PrePersist
    void alGuardar() {
        if (fechaPago == null) fechaPago = LocalDateTime.now();
    }
}
