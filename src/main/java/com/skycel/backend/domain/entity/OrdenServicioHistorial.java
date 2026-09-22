package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Bitácora de la orden: cada cambio de estado y cada hecho relevante (técnico asignado, anticipo...). */
@Entity
@Table(name = "orden_servicio_historial")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenServicioHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idhistorial")
    private Integer idhistorial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idorden", nullable = false)
    private OrdenServicio orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario")
    private Usuario usuario;

    /** Estado de la orden después del movimiento. */
    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT")
    private Byte estado;

    @Column(name = "comentario", columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "fecha", updatable = false)
    private LocalDateTime fecha;

    @jakarta.persistence.PrePersist
    void alGuardar() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
