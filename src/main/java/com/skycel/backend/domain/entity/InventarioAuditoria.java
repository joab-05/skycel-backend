package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Audited
@Table(name = "inventario_auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idinventario")
    private Integer idinventario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_auditor", nullable = false)
    private Usuario usuarioAuditor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_encargado_tienda", nullable = false)
    private Usuario usuarioEncargadoTienda;

    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte estado;

    @Column(name = "fecha_inicio", updatable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "total_faltantes", columnDefinition = "INT DEFAULT 0")
    private Integer totalFaltantes;

    @Column(name = "total_sobrantes", columnDefinition = "INT DEFAULT 0")
    private Integer totalSobrantes;
}
