package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Orden de servicio del taller: un cliente deja un equipo para repararlo.
 *
 * estado: 1 = Recibida, 2 = En reparación, 3 = Lista para entrega, 4 = Entregada, 5 = Cancelada.
 * Al entregar se genera una venta con los servicios y refacciones; los anticipos ya cobrados figuran
 * en esa venta como una línea de pago de tipo ANTICIPO.
 */
@Entity
@Audited
@Table(name = "orden_servicio", indexes = {
        @Index(name = "idx_orden_tienda_estado", columnList = "codti, estado"),
        @Index(name = "idx_orden_fecha_ingreso", columnList = "fecha_ingreso")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrdenServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idorden")
    private Integer idorden;

    /** Folio público para que el cliente dé seguimiento (OS-000001). */
    @Column(name = "folio", nullable = false, length = 20, unique = true)
    private String folio;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcliente", nullable = false)
    private Cliente cliente;

    /** Quien recibió el equipo. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_recibe", nullable = false)
    private Usuario usuarioRecibe;

    /** Técnico asignado (rol TECNICO). */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_tecnico")
    private Usuario tecnico;

    // ── Equipo que se deja ────────────────────────────────────────────────────

    @Column(name = "marca", length = 60)
    private String marca;

    @Column(name = "modelo", length = 120)
    private String modelo;

    /** IMEI o número de serie (opcional: no siempre se puede leer al recibir el equipo). */
    @Column(name = "imei", length = 20)
    private String imei;

    /** Lo que el cliente deja junto con el equipo (chip, funda, cargador...). */
    @Column(name = "accesorios_dejados", length = 255)
    private String accesoriosDejados;

    /** Golpes, rayones o cualquier daño previo, para deslindar responsabilidad. */
    @Column(name = "estado_fisico", columnDefinition = "TEXT")
    private String estadoFisico;

    @Column(name = "falla_reportada", nullable = false, columnDefinition = "TEXT")
    private String fallaReportada;

    /** Lo que encontró el técnico. */
    @Column(name = "diagnostico", columnDefinition = "TEXT")
    private String diagnostico;

    @Column(name = "estado", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Byte estado;

    @Column(name = "fecha_ingreso", updatable = false)
    private LocalDateTime fechaIngreso;

    @jakarta.persistence.PrePersist
    void alGuardar() {
        if (fechaIngreso == null) fechaIngreso = LocalDateTime.now();
    }

    /** Clave unica de una recepcion hecha sin conexion: reenviarla no duplica la orden. */
    @Column(name = "clave_offline", length = 64, unique = true)
    private String claveOffline;

    /** Folio provisional que se dio al cliente si el equipo se recibio sin conexion. */
    @Column(name = "folio_local", length = 40)
    private String folioLocal;

    /** Fecha en que se le prometió el equipo al cliente. */
    @Column(name = "fecha_promesa")
    private LocalDate fechaPromesa;

    /** Tiempo estimado de entrega que se le dio al cliente, en horas (contadas desde que se estimó). */
    @Column(name = "horas_estimadas")
    private Integer horasEstimadas;

    /** Día y hora a los que se prometió el equipo; se calcula con {@link #horasEstimadas}. */
    @Column(name = "fecha_promesa_hora")
    private LocalDateTime fechaPromesaHora;

    @Column(name = "fecha_lista")
    private LocalDateTime fechaLista;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    /** Días de garantía del trabajo y hasta cuándo aplica (se fijan al entregar). */
    @Column(name = "dias_garantia")
    private Integer diasGarantia;

    @Column(name = "fecha_garantia_hasta")
    private LocalDate fechaGarantiaHasta;

    /** La venta generada al entregar. */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idventa")
    private Venta venta;

    @Column(name = "motivo_cancelacion", length = 255)
    private String motivoCancelacion;

    /**
     * Autorización del cliente para el costo de la reparación: null = todavía no se cotiza; 1 = esperando su respuesta;
     * 2 = autorizó; 3 = no autorizó. Si el total cambia después de autorizado, vuelve a 1.
     */
    @Column(name = "autorizacion_estado")
    private Byte autorizacionEstado;

    /** El total que se cotizó (esperando) o que el cliente autorizó. */
    @Column(name = "autorizacion_total", precision = 12, scale = 2)
    private java.math.BigDecimal autorizacionTotal;

    /** El total que ya estaba autorizado antes de un cambio de precio (para decirle al cliente de cuánto a cuánto). */
    @Column(name = "autorizacion_total_previo", precision = 12, scale = 2)
    private java.math.BigDecimal autorizacionTotalPrevio;

    /** Cómo respondió el cliente (en persona, llamada, WhatsApp) y quién lo registró. */
    @Column(name = "autorizacion_medio", length = 30)
    private String autorizacionMedio;

    @Column(name = "autorizacion_nota", length = 255)
    private String autorizacionNota;

    @Column(name = "autorizacion_por", length = 150)
    private String autorizacionPor;

    @Column(name = "autorizacion_fecha")
    private LocalDateTime autorizacionFecha;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Version
    @Column(name = "version")
    private Integer version;
}
