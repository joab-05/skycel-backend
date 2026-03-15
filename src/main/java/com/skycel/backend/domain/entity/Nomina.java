package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nomina")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nomina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idnomina")
    private Integer idnomina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_paga", nullable = false)
    private Usuario usuarioPaga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    @Column(name = "periodo_descripcion", nullable = false, length = 100)
    private String periodoDescripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @CreationTimestamp
    @Column(name = "fecha_ejecucion", updatable = false)
    private LocalDateTime fechaEjecucion;

    @Column(name = "total_nomina", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalNomina;

    @Version
    @Column(name = "version")
    private Integer version;
}
