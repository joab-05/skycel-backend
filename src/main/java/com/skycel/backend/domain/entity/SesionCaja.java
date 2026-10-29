package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un turno de una caja: se abre al iniciar el día con un fondo inicial y se cierra al terminar con el efectivo contado.
 * Al cerrar queda guardado el corte completo ({@link #detalle}: ventas por método, entradas y salidas por motivo), para
 * poder consultarlo y reimprimirlo después. Una caja solo puede tener una sesión abierta a la vez.
 */
@Entity
@Table(name = "sesion_caja", indexes = @Index(name = "idx_sesion_caja_caja_estado", columnList = "id_caja, estado"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SesionCaja {

    public static final byte ABIERTA = 1;
    public static final byte CERRADA = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idsesion_caja")
    private Integer idsesionCaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_apertura", nullable = false)
    private Usuario usuarioApertura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_cierre")
    private Usuario usuarioCierre;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    /** 1 abierta, 2 cerrada. */
    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT")
    private Byte estado;

    /** Efectivo con el que se abrió el turno. */
    @Column(name = "fondo_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal fondoInicial;

    // ── Se llenan al cerrar ───────────────────────────────────────────────────

    /** Lo que el sistema decía que debía haber en el cajón al cerrar. */
    @Column(name = "efectivo_esperado", precision = 12, scale = 2)
    private BigDecimal efectivoEsperado;

    /** Lo que se contó de verdad. */
    @Column(name = "efectivo_contado", precision = 12, scale = 2)
    private BigDecimal efectivoContado;

    /** Contado menos esperado (negativo = faltante). */
    @Column(name = "diferencia", precision = 12, scale = 2)
    private BigDecimal diferencia;

    /** Lo que se deja en el cajón para el siguiente turno. */
    @Column(name = "fondo_siguiente", precision = 12, scale = 2)
    private BigDecimal fondoSiguiente;

    /** Lo que se retiró del cajón al cerrar (contado menos fondo siguiente). */
    @Column(name = "retiro_cierre", precision = 12, scale = 2)
    private BigDecimal retiroCierre;

    /** Claves de lo que se hizo sin conexión: reenviar la misma apertura o el mismo cierre no lo duplica. */
    @Column(name = "clave_apertura", length = 64)
    private String claveApertura;

    @Column(name = "clave_cierre", length = 64)
    private String claveCierre;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    /**
     * Qué pasó con el faltante de este corte (cuando la diferencia es negativa): null = todavía no se descuenta a nadie,
     * 1 = ya es una deducción de nómina, 2 = el administrador lo perdonó (quitó la deducción).
     */
    @Column(name = "faltante_nomina", columnDefinition = "TINYINT")
    private Byte faltanteNomina;

    /** El corte completo al cerrar, en JSON (ver SesionCajaResponseDto.ResumenDto). */
    @Column(name = "detalle", columnDefinition = "MEDIUMTEXT")
    private String detalle;

    @Version
    @Column(name = "version")
    private Integer version;
}
