package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Comisión de un empleado en un mes: la de encargado (2% mensual de su tienda) o la de PayJoy (por equipo
 * vendido a crédito). Mientras está PENDIENTE se recalcula en vivo cada vez que se consulta (refleja ventas y
 * devoluciones del momento); al marcarla PAGADA queda congelada con el monto exacto que se pagó, aunque después
 * llegue una devolución tardía de ese mes — así se evita descontar algo que el empleado ya cobró.
 */
@Entity
@Table(name = "comision_periodo", uniqueConstraints = @UniqueConstraint(columnNames = {"tipo", "idempleado", "anio", "mes"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComisionPeriodo {

    public static final byte TIPO_ENCARGADO_MENSUAL = 1;
    public static final byte TIPO_VENDEDOR_PAYJOY = 2;

    public static final byte ESTADO_PENDIENTE = 0;
    public static final byte ESTADO_PAGADA = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idcomision")
    private Integer idcomision;

    @Column(name = "tipo", nullable = false)
    private Byte tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idempleado", nullable = false)
    private EmpleadoPerfil empleado;

    /** Informativo: la tienda del encargado. Null en una comisión PayJoy (el vendedor puede rotar de sucursal). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti")
    private Tienda tienda;

    @Column(name = "anio", nullable = false)
    private Integer anio;

    @Column(name = "mes", nullable = false)
    private Integer mes;

    /** La base sobre la que se calculó: ventas del mes (encargado) o suma de precios de equipo (PayJoy). */
    @Column(name = "venta_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal ventaBase;

    /** Solo informativo en la de encargado (una sola tasa); null en PayJoy, que mezcla dos tasas por rango de precio. */
    @Column(name = "tasa_aplicada", precision = 5, scale = 2)
    private BigDecimal tasaAplicada;

    @Column(name = "comision", nullable = false, precision = 12, scale = 2)
    private BigDecimal comision;

    @Column(name = "estado", nullable = false)
    private Byte estado;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario_pago")
    private Usuario pagadaPor;

    @CreationTimestamp
    @Column(name = "creado_el")
    private LocalDateTime creadoEl;
}
