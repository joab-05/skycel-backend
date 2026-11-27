package com.skycel.backend.domain.entity;

import com.skycel.backend.domain.enums.MomentoTarea;
import com.skycel.backend.domain.enums.Rol;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Una actividad de la rutina diaria de una tienda (por ejemplo «Abrir la caja» en la apertura). Cada día aplicable el sistema
 * crea de ella una {@link Tarea}; la rutina solo es la plantilla.
 */
@Entity
@Table(name = "tarea_rutina")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TareaRutina {

    /** Todos los días de la semana (1 = lunes ... 7 = domingo). */
    public static final String TODOS_LOS_DIAS = "1,2,3,4,5,6,7";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idrutina")
    private Integer idrutina;

    /** La tienda a la que aplica; sin tienda, aplica a todas las tiendas activas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti")
    private Tienda tienda;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "momento", nullable = false, length = 20)
    private MomentoTarea momento;

    /** Posición dentro del momento del día (de menor a mayor). */
    @Column(name = "orden", nullable = false)
    @Builder.Default
    private Integer orden = 0;

    /** A qué rol le toca; sin rol, a cualquiera de la tienda. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rol_destino", length = 30)
    private Rol rolDestino;

    /** Días en que aplica, separados por coma (1 = lunes ... 7 = domingo). */
    @Column(name = "dias_semana", nullable = false, length = 20)
    @Builder.Default
    private String diasSemana = TODOS_LOS_DIAS;

    /** Hora a la que debería estar hecha; sin hora, basta con que se haga ese día. */
    @Column(name = "hora_limite")
    private LocalTime horaLimite;

    @Column(name = "activa", nullable = false)
    @Builder.Default
    private Boolean activa = true;

    @Column(name = "creado_por", length = 50)
    private String creadoPor;

    @CreationTimestamp
    @Column(name = "creado_el", updatable = false)
    private LocalDateTime creadoEl;

    @UpdateTimestamp
    @Column(name = "modificado_el")
    private LocalDateTime modificadoEl;

    public boolean aplicaEn(DayOfWeek dia) {
        if (diasSemana == null) return false;
        for (String d : diasSemana.split(",")) {
            if (d.trim().equals(String.valueOf(dia.getValue()))) return true;
        }
        return false;
    }
}
