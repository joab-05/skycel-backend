package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Aviso para un usuario de que algo que le interesa cambió (garantía, orden de servicio, traspaso...). */
@Entity
@Table(name = "notificacion", indexes = {
        @Index(name = "idx_notificacion_destino_leida", columnList = "idusuario_destino, leida")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idnotificacion")
    private Integer idnotificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_destino", nullable = false)
    private Usuario usuarioDestino;

    /** GARANTIA_AVANCE, ORDEN_NUEVA, ORDEN_AVANCE, TRASPASO_SOLICITUD. */
    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo;

    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    /** Ruta de la web para ir directo al registro (ej. "#/garantia/12"); null si no aplica (p. ej. traspasos, solo en JSystem). */
    @Column(name = "ruta", length = 100)
    private String ruta;

    @Column(name = "leida", nullable = false)
    @Builder.Default
    private Boolean leida = false;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_leida")
    private LocalDateTime fechaLeida;
}
