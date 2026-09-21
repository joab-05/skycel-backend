package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Historial de cada cambio en el stock de un producto: qué pasó (tipo), cuánto (cantidad con signo), cómo quedó el
 * stock, por qué (motivo), de qué operación viene (referencia) y quién lo hizo.
 *
 * tipo: ALTA (stock inicial), ENTRADA, SALIDA, AJUSTE (captura manual), VENTA, CANCELACION_VENTA,
 *       TRASPASO_SALIDA, TRASPASO_ENTRADA, TRASPASO_ANULADO, TRASPASO_FALTANTE (regresa o llega lo que faltó).
 */
@Entity
@Table(name = "movimiento_inventario", indexes = {
        @Index(name = "idx_mov_inv_producto", columnList = "idproducto"),
        @Index(name = "idx_mov_inv_fecha", columnList = "fecha")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idmov")
    private Long idmov;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproducto", nullable = false)
    private Producto producto;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    /** Con signo: positivo entra, negativo sale. */
    @Column(name = "cantidad", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "stock_antes", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockAntes;

    @Column(name = "stock_despues", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockDespues;

    @Column(name = "motivo", length = 255)
    private String motivo;

    /** La operación que lo originó, p. ej. "Venta #12" o "Traspaso #16". */
    @Column(name = "referencia", length = 60)
    private String referencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario")
    private Usuario usuario;

    @Column(name = "fecha", updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    void alGuardar() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
