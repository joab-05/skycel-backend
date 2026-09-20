package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bitácora de un faltante: cuándo se reportó y cada resolución (qué se hizo, cuánto, quién y por qué).
 * accion: REPORTADO, RECIBIDO_TARDE, REINTEGRADO_ORIGEN o BAJA.
 */
@Entity
@Table(name = "traspaso_faltante_mov")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TraspasoFaltanteMov {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idmov")
    private Integer idmov;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iddetalle", nullable = false)
    private TraspasoDetalle detalle;

    @Column(name = "accion", nullable = false, length = 20)
    private String accion;

    @Column(name = "cantidad", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario", nullable = false)
    private Usuario usuario;

    @Column(name = "nota", length = 255)
    private String nota;

    @CreationTimestamp
    @Column(name = "fecha", updatable = false)
    private LocalDateTime fecha;
}
