package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Un tipo de servicio que se opera fuera del sistema de ventas (recargas, pagos de servicios, pines electrónicos, pagos
 * PayJoy...) y que se reporta en el corte diario. Lo que gana el negocio con cada uno se define así:
 *  - un porcentaje de lo vendido (p. ej. Servicel 8.1 %, Tae Recargas 7.6 % de cada saldo);
 *  - una comisión fija que se le cobra al cliente por operación (p. ej. $15 en pagos de servicios) menos lo que el proveedor
 *    le cobra al negocio por esa operación (p. ej. $4): quedan $11 de ganancia;
 *  - o solo se cuentan las operaciones (los pagos PayJoy: sirven para saber cuántos clientes pagan aquí).
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

    /** Lo que se le cobra al cliente por cada operación (0 = no se cobra comisión). */
    @Column(name = "comision_por_operacion", nullable = false, precision = 10, scale = 2)
    private BigDecimal comisionPorOperacion;

    /** Porcentaje de lo vendido que queda de ganancia (8.1 = 8.1 %). */
    @Builder.Default
    @Column(name = "porcentaje_ganancia", nullable = false, precision = 5, scale = 2, columnDefinition = "DECIMAL(5,2) NOT NULL DEFAULT 0")
    private BigDecimal porcentajeGanancia = BigDecimal.ZERO;

    /** Lo que el proveedor le cobra al negocio por cada operación; se descuenta de la comisión. */
    @Builder.Default
    @Column(name = "costo_por_operacion", nullable = false, precision = 10, scale = 2, columnDefinition = "DECIMAL(10,2) NOT NULL DEFAULT 0")
    private BigDecimal costoPorOperacion = BigDecimal.ZERO;

    /** true = solo se cuentan las operaciones (no se calcula ganancia), como los pagos PayJoy. */
    @Builder.Default
    @Column(name = "solo_conteo", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private Boolean soloConteo = false;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "activo", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean activo;
}
