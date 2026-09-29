package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nomina_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NominaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddetalle")
    private Integer iddetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idperiodo", nullable = false)
    private NominaPeriodo periodo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idempleado", nullable = false)
    private EmpleadoPerfil empleado;

    // 0 = PENDIENTE, 1 = PAGADO
    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte estado;

    @Column(name = "total_percepciones", nullable = false, precision = 12, scale = 2, columnDefinition = "DECIMAL(12,2) DEFAULT 0.00")
    private BigDecimal totalPercepciones;

    @Column(name = "total_deducciones", nullable = false, precision = 12, scale = 2, columnDefinition = "DECIMAL(12,2) DEFAULT 0.00")
    private BigDecimal totalDeducciones;

    @Column(name = "total_neto", nullable = false, precision = 12, scale = 2, columnDefinition = "DECIMAL(12,2) DEFAULT 0.00")
    private BigDecimal totalNeto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcaja")
    private Caja caja;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;
}
