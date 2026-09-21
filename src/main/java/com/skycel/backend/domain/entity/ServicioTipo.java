package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Un tipo de servicio que se opera fuera del sistema de ventas (recargas, pagos de servicios, pines electrónicos, pagos
 * PayJoy...) y que se reporta en el corte diario. La comisión es un monto fijo que el negocio cobra por operación.
 */
@Entity
@Table(name = "servicio_tipo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServicioTipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idtipo")
    private Integer idtipo;

    @Column(name = "nombre", nullable = false, unique = true, length = 80)
    private String nombre;

    /** Lo que gana el negocio por cada operación (0 = no cobra comisión). */
    @Column(name = "comision_por_operacion", nullable = false, precision = 10, scale = 2)
    private BigDecimal comisionPorOperacion;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "activo", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean activo;
}
