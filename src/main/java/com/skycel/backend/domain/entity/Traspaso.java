package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;

/**
 * Traspaso de mercancía entre tiendas (logística de doble vía).
 *
 * tipo:   1 = ENVIO (la tienda origen manda mercancía a la destino)
 *         2 = SOLICITUD (la tienda origen le pide mercancía a la destino)
 * estado: 1 = Enviado, 2 = Recibido (físico), 3 = Leído, 4 = Aceptada, 5 = Rechazada
 *         Un envío termina en Recibido; una solicitud termina en Aceptada (y genera el envío
 *         ligado por idtraspasoRef) o Rechazada.
 * activo: false = anulado.
 */
@Entity
@Audited
@Table(name = "traspaso")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Traspaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idtraspaso")
    private Integer idtraspaso;

    @Column(name = "tipo", nullable = false, columnDefinition = "TINYINT")
    private Byte tipo;

    /** Tienda donde se crea (quien envía en un ENVIO, quien pide en una SOLICITUD). */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti_origen", nullable = false)
    private Tienda tiendaOrigen;

    /** Tienda a la que va dirigido (quien recibe en un ENVIO, quien surte en una SOLICITUD). */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti_destino", nullable = false)
    private Tienda tiendaDestino;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_crea", nullable = false)
    private Usuario usuarioCrea;

    /** Quien recibió, leyó, aceptó o rechazó. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_valida")
    private Usuario usuarioValida;

    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Byte estado;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    /** En un envío generado al aceptar una solicitud: el id de esa solicitud. */
    @Column(name = "idtraspaso_ref")
    private Integer idtraspasoRef;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "motivo_rechazo", length = 255)
    private String motivoRechazo;

    /** true si al recibir el envío faltó mercancía (ver TraspasoDetalle: cantidadRecibida). */
    @Column(name = "con_faltantes", columnDefinition = "TINYINT(1) DEFAULT 0")
    @Builder.Default
    private Boolean conFaltantes = false;

    /** Lo que anotó quien recibió (obligatorio cuando hubo faltantes). */
    @Column(name = "comentario_recepcion", length = 255)
    private String comentarioRecepcion;

    @Version
    @Column(name = "version")
    private Integer version;
}
