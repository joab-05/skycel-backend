package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Foto de evidencia de una tarea. La imagen ya viene reducida desde el cliente; se guarda en la base de datos para que viaje en los respaldos
 * y se borra sola con la limpieza automática pasados unos días.
 */
@Entity
@Table(name = "tarea_foto", indexes = @Index(name = "idx_tarea_foto_tarea", columnList = "idtarea"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TareaFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idfoto")
    private Integer idfoto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idtarea", nullable = false)
    private Tarea tarea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario")
    private Usuario usuario;

    /** image/jpeg o image/png */
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "contenido", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contenido;

    @Column(name = "fecha", updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    void alGuardar() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
