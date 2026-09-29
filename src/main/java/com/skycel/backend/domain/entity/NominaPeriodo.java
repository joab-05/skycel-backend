package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nomina_periodo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NominaPeriodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idperiodo")
    private Integer idperiodo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    // 0 = ABIERTO, 1 = CERRADO
    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte estado;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;
}
