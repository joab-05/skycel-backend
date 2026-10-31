package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Foto del equipo al recibirlo (o durante la reparación). La imagen ya viene reducida desde el cliente; se guarda en la base de datos
 * para que viaje en los respaldos. Las de órdenes ya cerradas se borran solas con la limpieza automática.
 */
@Entity
@Table(name = "orden_servicio_foto", indexes = @Index(name = "idx_foto_orden", columnList = "idorden"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenServicioFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idfoto")
    private Integer idfoto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idorden", nullable = false)
    private OrdenServicio orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario")
    private Usuario usuario;

    /** Qué muestra (p. ej. «pantalla estrellada»). Opcional. */
    @Column(name = "descripcion", length = 120)
    private String descripcion;

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
