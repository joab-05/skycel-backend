package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Regla de descuento automático: el sistema la aplica solo, al vender, sin que un vendedor ni un
 * administrador toquen el precio a mano — es la válvula controlada para dar descuentos (cliente
 * frecuente, liquidación, promoción de fin de semana) sin reabrir el precio libre en el punto de venta.
 * Alcance por columnas planas (no relaciones) a propósito: así el cálculo no depende de la sesión de
 * Hibernate y el historial de cambios (ver AuditoriaService) puede comparar sus campos sin arriesgar
 * cargar algo fuera de sesión.
 */
@Entity
@Audited
@Table(name = "descuento_regla")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DescuentoRegla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddescuento")
    private Integer iddescuento;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** null = aplica a cualquier tipo de artículo. */
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "tipo_producto")
    private com.skycel.backend.domain.enums.TipoProducto tipoProducto;

    /** null = aplica a cualquier artículo (de ese tipo, si se indicó). Para un descuento a un solo modelo. */
    @Column(name = "idprodmaster")
    private Integer idProductoMaster;

    /** null = aplica en todas las sucursales. */
    @Column(name = "codti")
    private Integer codti;

    /** null o 0 = sin mínimo: aplica a cualquier precio. */
    @Column(name = "monto_minimo", precision = 12, scale = 2)
    private BigDecimal montoMinimo;

    /** Exactamente uno de los dos (validado en el servicio): porcentaje del precio, o monto fijo en pesos. */
    @Column(name = "descuento_porcentaje", precision = 5, scale = 2)
    private BigDecimal descuentoPorcentaje;

    @Column(name = "descuento_fijo", precision = 12, scale = 2)
    private BigDecimal descuentoFijo;

    /** 0 = SIEMPRE, 1 = DIAS_SEMANA (ver {@code diasSemana}), 2 = RANGO_FECHA (ver fechaInicio/fechaFin). */
    @Column(name = "aplicacion", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private Byte aplicacion;

    /** Solo si aplicacion = DIAS_SEMANA. Texto corto tipo "LUN,MIE,VIE" (nombres de {@link java.time.DayOfWeek} en español, 3 letras). */
    @Column(name = "dias_semana", length = 30)
    private String diasSemana;

    /** Solo si aplicacion = RANGO_FECHA. */
    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;
}
