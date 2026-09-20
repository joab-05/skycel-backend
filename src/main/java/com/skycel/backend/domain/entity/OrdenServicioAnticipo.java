package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Anticipo que el cliente deja al recibir el equipo (o después). Solo el efectivo entra a la caja. */
@Entity
@Table(name = "orden_servicio_anticipo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenServicioAnticipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idanticipo")
    private Integer idanticipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idorden", nullable = false)
    private OrdenServicio orden;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** 1 = Efectivo, 2 = Tarjeta, 3 = Transferencia */
    @Column(name = "metodo_pago", nullable = false, columnDefinition = "TINYINT")
    private Byte metodoPago;

    /** Folio del voucher o número de rastreo (opcional). */
    @Column(name = "folio_operacion", length = 50)
    private String folioOperacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario", nullable = false)
    private Usuario usuario;

    /** El movimiento de caja que generó (solo si fue en efectivo), para poder devolverlo. */
    @Column(name = "idmovimiento_caja")
    private Integer idmovimientoCaja;

    @CreationTimestamp
    @Column(name = "fecha", updatable = false)
    private LocalDateTime fecha;
}
