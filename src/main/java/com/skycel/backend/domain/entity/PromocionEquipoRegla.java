package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Una regla de la promoción al vender un equipo: por cada equipo que se lleva el cliente, tantas piezas de los artículos de cierta
 * categoría (y, si se quiere, de cierto rango de precio) reciben un descuento. Las reglas se revisan en orden y a cada pieza le toca
 * la primera que le corresponde y todavía tiene cupo. Columnas planas (no relaciones), igual que {@link DescuentoRegla}.
 *
 * tipo: REGALO (queda en $0), PORCENTAJE (valor = % de descuento), MONTO (valor = pesos de descuento por pieza; si el artículo cuesta
 * menos, queda gratis) o PRECIO_FINAL (valor = lo que paga por la pieza).
 * redondeo (del precio que queda): NINGUNO, PESO_ARRIBA, PESO_ABAJO, CINCO_ARRIBA, CINCO_ABAJO, DIEZ_ARRIBA o DIEZ_ABAJO.
 */
@Entity
@Table(name = "promocion_equipo_regla")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionEquipoRegla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idregla")
    private Integer idregla;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    /** Categoría de los artículos (vale también para sus subcategorías). */
    @Column(name = "idcat", nullable = false)
    private Short idCategoria;

    /** Rango del precio de lista al que aplica (los dos extremos incluidos); null = sin límite. */
    @Column(name = "precio_desde", precision = 12, scale = 2)
    private BigDecimal precioDesde;

    @Column(name = "precio_hasta", precision = 12, scale = 2)
    private BigDecimal precioHasta;

    /** Cuántas piezas recibe la promoción por cada equipo vendido. */
    @Column(name = "piezas_por_equipo", nullable = false)
    private Integer piezasPorEquipo;

    @Column(name = "tipo", nullable = false, length = 12)
    private String tipo;

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "redondeo", nullable = false, length = 14)
    private String redondeo;

    @Column(name = "activa", nullable = false)
    private Boolean activa;

    /** Las tres reglas de la primera versión (CHIP, MICA, PROTECTOR) conservan su clave para contar lo ya entregado en una misma operación. */
    @Column(name = "clave_legacy", length = 12)
    private String claveLegacy;

    /** Con qué clave se cuentan las piezas que ya recibió esta regla en las ventas de una misma operación. */
    public String clave() {
        return claveLegacy != null ? claveLegacy : "R" + idregla;
    }
}
