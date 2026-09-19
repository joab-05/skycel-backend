package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pago_cuenta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PagoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idpago")
    private Integer idpago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcuenta", nullable = false)
    private CuentaPorCobrar cuentaPorCobrar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario", nullable = false)
    private Usuario usuario;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /**
     * 1=Efectivo 2=Tarjeta 3=Transferencia 4=PayJoy
     */
    @Column(name = "metodo_pago", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Byte metodoPago = 1;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @CreationTimestamp
    @Column(name = "fecha_pago", updatable = false)
    private LocalDateTime fechaPago;
}