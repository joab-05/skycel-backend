package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Lo que se operó de un servicio en el día. Guarda el nombre y la comisión vigentes, por si el catálogo cambia después. */
@Entity
@Table(name = "corte_servicios_linea")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CorteServiciosLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idlinea")
    private Integer idlinea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcorte", nullable = false)
    private CorteServicios corte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idtipo", nullable = false)
    private ServicioTipo tipo;

    @Column(name = "nombre_tipo", nullable = false, length = 80)
    private String nombreTipo;

    @Column(name = "comision_por_operacion", nullable = false, precision = 10, scale = 2)
    private BigDecimal comisionPorOperacion;

    @Column(name = "operaciones", nullable = false)
    private Integer operaciones;

    /** Importe de las operaciones (sin la comisión). */
    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** operaciones × comisión por operación. */
    @Column(name = "comision", nullable = false, precision = 12, scale = 2)
    private BigDecimal comision;

    /** Saldo del proveedor (en su plataforma) al empezar el día. Opcional. */
    @Column(name = "saldo_inicial", precision = 12, scale = 2)
    private BigDecimal saldoInicial;

    /** Saldo que se le agregó al proveedor durante el día (depósitos). Opcional. */
    @Column(name = "fondeo", precision = 12, scale = 2)
    private BigDecimal fondeo;

    /** Saldo del proveedor al cierre. Opcional. */
    @Column(name = "saldo_final", precision = 12, scale = 2)
    private BigDecimal saldoFinal;

    /** Lo que reporta el proveedor para el día; lo captura el administrador al confirmar. */
    @Column(name = "monto_proveedor", precision = 12, scale = 2)
    private BigDecimal montoProveedor;
}
