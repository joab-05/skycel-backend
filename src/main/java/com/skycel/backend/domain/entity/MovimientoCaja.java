package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Audited
@Table(name = "movimiento_caja")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idmovimiento")
    private Integer idmovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_encargado", nullable = false)
    private Usuario usuarioEncargado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_admin")
    private Usuario usuarioAdmin;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idsesion")
    private UsuarioSesion sesion;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idmotivo", nullable = false)
    private CatMotivo motivo;

    @Column(name = "tipo", nullable = false, columnDefinition = "TINYINT")
    private Byte tipo;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "idmov_ref")
    private Integer idmovRef;

    @Column(name = "fecha_mov", updatable = false)
    private LocalDateTime fechaMov;

    @PrePersist
    void alGuardar() {
        if (fechaMov == null) fechaMov = LocalDateTime.now();
    }

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
}
