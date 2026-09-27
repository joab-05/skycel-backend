package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Compra de mercancía a un proveedor: qué se recibió, en qué sucursal, a qué costo y cuánto se le debe.
 * Registrarla suma el stock del producto (mismo movimiento que un ajuste de tipo ENTRADA) y, si se indica costo,
 * actualiza el precio de compra del producto — la compra es la fuente natural de ese dato.
 */
@Entity
@Table(name = "compra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idcompra")
    private Integer idcompra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproveedor", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario")
    private Usuario usuario;

    /** Folio o número de factura del proveedor (tal como lo trae su documento); libre, puede repetirse. */
    @Column(name = "folio_proveedor", length = 40)
    private String folioProveedor;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "monto_pagado", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO;

    /**
     * 0 = Pendiente
     * 1 = PagadaParcial
     * 2 = Pagada
     * 3 = Vencida
     */
    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte estado = 0;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    @OneToMany(mappedBy = "compra", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<CompraDetalle> lineas;

    @OneToMany(mappedBy = "compra", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<PagoCompra> pagos;
}
