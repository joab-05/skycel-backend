package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Devolución de productos de una venta. La solicita el personal y la aprueba (o rechaza) un encargado o administrador;
 * al aprobarla se ejecuta: el producto regresa al inventario y se reembolsa o se cambia por otro.
 *
 * tipo:   1 = REEMBOLSO (se devuelve el dinero), 2 = CAMBIO (se lleva otro producto; genera una venta nueva)
 * estado: 1 = Pendiente, 2 = Procesada (aprobada y ejecutada), 3 = Rechazada
 */
@Entity
@Table(name = "devolucion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddevolucion")
    private Integer iddevolucion;

    @Column(name = "folio", nullable = false, unique = true, length = 20)
    private String folio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idventa", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @Column(name = "tipo", nullable = false, columnDefinition = "TINYINT")
    private Byte tipo;

    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT")
    private Byte estado;

    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    /** Lo que vale lo devuelto (a los precios a los que se vendió). */
    @Column(name = "total_devuelto", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDevuelto;

    /** Reembolso: cómo se devuelve el dinero (1 Efectivo, 2 Tarjeta, 3 Transferencia). Solo el efectivo mueve la caja. */
    @Column(name = "metodo_reembolso", columnDefinition = "TINYINT")
    private Byte metodoReembolso;

    /** Cambio: cómo paga el cliente la diferencia, si el producto nuevo vale más (1, 2 o 3). */
    @Column(name = "metodo_diferencia", columnDefinition = "TINYINT")
    private Byte metodoDiferencia;

    @Column(name = "folio_operacion", length = 50)
    private String folioOperacion;

    /** Caja que se usa (reembolso en efectivo o diferencia del cambio). Por defecto, la de la venta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcaja")
    private Caja caja;

    /** Cambio: la venta nueva que se generó al aprobar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idventa_cambio")
    private Venta ventaCambio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_solicita", nullable = false)
    private Usuario usuarioSolicita;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_resuelve")
    private Usuario usuarioResuelve;

    @Column(name = "comentario_resolucion", length = 255)
    private String comentarioResolucion;

    @CreationTimestamp
    @Column(name = "fecha_solicitud", updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;
}
