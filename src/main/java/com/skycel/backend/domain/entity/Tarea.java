package com.skycel.backend.domain.entity;

import com.skycel.backend.domain.enums.EstadoTarea;
import com.skycel.backend.domain.enums.MomentoTarea;
import com.skycel.backend.domain.enums.Rol;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * La actividad de un día concreto en una tienda. Guarda su propio texto: si después se cambia la rutina, lo que ya se generó (y
 * su historial) no se altera.
 */
@Entity
@Table(name = "tarea",
        uniqueConstraints = @UniqueConstraint(name = "uk_tarea_rutina_tienda_fecha", columnNames = {"idrutina", "codti", "fecha"}),
        indexes = @Index(name = "ix_tarea_tienda_fecha", columnList = "codti, fecha"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idtarea")
    private Integer idtarea;

    /** La rutina de la que salió; sin rutina, es una tarea suelta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idrutina")
    private TareaRutina rutina;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "momento", nullable = false, length = 20)
    private MomentoTarea momento;

    @Column(name = "orden", nullable = false)
    @Builder.Default
    private Integer orden = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_destino", length = 30)
    private Rol rolDestino;

    @Column(name = "hora_limite")
    private LocalTime horaLimite;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoTarea estado = EstadoTarea.PENDIENTE;

    @Column(name = "nota", length = 500)
    private String nota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_hecha")
    private Usuario hechaPor;

    @Column(name = "hecha_el")
    private LocalDateTime hechaEl;

    /** Para una tarea puntual: la persona a la que se le asignó (sin persona, es de todo su puesto o de cualquiera de la tienda). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_asignado")
    private Usuario asignadoA;

    /** Al marcarla queda «por revisar» en lugar de «hecha»: la aprueba o la regresa otra persona. */
    @Column(name = "requiere_revision")
    @Builder.Default
    private Boolean requiereRevision = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_revisa")
    private Usuario revisadaPor;

    @Column(name = "revisada_el")
    private LocalDateTime revisadaEl;

    /** Lo que dijo quien la regresó (se conserva mientras la tarea vuelve a quedar pendiente). */
    @Column(name = "comentario_revision", length = 500)
    private String comentarioRevision;

    /** Quién creó la tarea puntual (usuario). */
    @Column(name = "creada_por", length = 50)
    private String creadaPor;

    /** Si se marcó después de su hora límite. */
    @Column(name = "tarde")
    private Boolean tarde;

    public boolean esPuntual() { return rutina == null; }

    public boolean terminada() { return estado != null && estado != EstadoTarea.PENDIENTE; }

    @CreationTimestamp
    @Column(name = "creado_el", updatable = false)
    private LocalDateTime creadoEl;

    @Version
    @Column(name = "version")
    private Integer version;
}
