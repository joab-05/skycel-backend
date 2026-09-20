package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Bitácora de una garantía: cada cambio de estado, con quién lo hizo y un comentario. */
@Entity
@Table(name = "garantia_historial")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GarantiaHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idhistorial")
    private Integer idhistorial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idgarantia", nullable = false)
    private Garantia garantia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_movimiento")
    private Usuario usuario;

    /** Estado de la garantía después del movimiento. */
    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT")
    private Byte estado;

    @Column(name = "comentario", columnDefinition = "TEXT")
    private String comentario;

    @CreationTimestamp
    @Column(name = "fecha_movimiento", updatable = false)
    private LocalDateTime fechaMovimiento;
}
