package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un descuento que un vendedor o encargado pide a un administrador para una línea de la venta (un descuento puntual, fuera de las
 * reglas automáticas). Si el administrador lo aprueba, la línea se puede vender con ese descuento una sola vez; el servidor
 * valida la autorización al cobrar, así que el punto de venta no puede inventar descuentos.
 */
@Entity
@Table(name = "solicitud_descuento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudDescuento {

    public static final byte PENDIENTE = 0;
    public static final byte APROBADA = 1;
    public static final byte RECHAZADA = 2;
    public static final byte USADA = 3;
    public static final byte CANCELADA = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idsolicitud")
    private Integer idsolicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idusuario_solicita", nullable = false)
    private Usuario solicitante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idprodmaster", nullable = false)
    private ProductoMaster productoMaster;

    /** Equipos: la unidad a la que se pide el descuento. */
    @Column(name = "imei", length = 20)
    private String imei;

    @Column(name = "codpro", length = 25)
    private String codpro;

    @Column(name = "cantidad", nullable = false)
    private Short cantidad;

    /** El precio que tenía la línea cuando se pidió (para que quien aprueba vea de cuánto se trata). */
    @Column(name = "precio_lista", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioLista;

    /** Lo que se pide (y, al aprobar, lo que se autoriza) de descuento por cada pieza. */
    @Column(name = "descuento_por_pieza", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoPorPieza;

    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    @Column(name = "estado", nullable = false)
    private Byte estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_resuelve")
    private Usuario resolvio;

    @Column(name = "motivo_resolucion", length = 255)
    private String motivoResolucion;

    @CreationTimestamp
    @Column(name = "fecha_solicitud", updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    /** La venta en la que se usó. */
    @Column(name = "idventa")
    private Integer idventa;
}
