package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Corte diario de servicios de una sucursal (recargas, pagos de servicios, pines, pagos PayJoy). Lo captura el encargado al
 * cierre del día y un administrador lo confirma contra los reportes de cada proveedor. No mueve ventas ni caja.
 *
 * estado: 1 = Capturado, 2 = Confirmado, 3 = Confirmado con diferencias
 */
@Entity
@Table(name = "corte_servicios", uniqueConstraints = @UniqueConstraint(name = "uk_corte_servicios_tienda_fecha", columnNames = {"codti", "fecha"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CorteServicios {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idcorte")
    private Integer idcorte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT")
    private Byte estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_captura", nullable = false)
    private Usuario usuarioCaptura;

    @Column(name = "observaciones", length = 255)
    private String observaciones;

    @CreationTimestamp
    @Column(name = "fecha_captura", updatable = false)
    private LocalDateTime fechaCaptura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_confirma")
    private Usuario usuarioConfirma;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    @Column(name = "comentario_confirmacion", length = 255)
    private String comentarioConfirmacion;
}
