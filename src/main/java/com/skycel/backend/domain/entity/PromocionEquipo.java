package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

/**
 * La promoción al vender un equipo (celular o tablet): por cada equipo, un chip de regalo, una mica con descuento y un protector
 * con descuento si es barato. Es una sola configuración (la fila 1): las categorías de chips, micas y protectores se eligen del árbol
 * de categorías y valen también para sus subcategorías. Columnas planas (no relaciones), igual que {@link DescuentoRegla}.
 */
@Entity
@Audited
@Table(name = "promocion_equipo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionEquipo {

    public static final int ID_UNICO = 1;

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /** Categoría de los chips (Telcel, Bait...): el chip va de regalo. null = la promoción no incluye chips. */
    @Column(name = "idcat_chip")
    private Short idCategoriaChip;

    /** Categoría de las micas: a la mica se le descuenta {@link #descuentoMica} (si cuesta eso o menos, va de regalo). */
    @Column(name = "idcat_mica")
    private Short idCategoriaMica;

    /** Categoría de los protectores: se les descuenta {@link #descuentoProtector} si cuestan menos de {@link #protectorPrecioMenorA}. */
    @Column(name = "idcat_protector")
    private Short idCategoriaProtector;

    @Column(name = "descuento_mica", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoMica;

    @Column(name = "descuento_protector", nullable = false, precision = 12, scale = 2)
    private BigDecimal descuentoProtector;

    @Column(name = "protector_precio_menor_a", nullable = false, precision = 12, scale = 2)
    private BigDecimal protectorPrecioMenorA;

    /** Cuántas piezas de cada cosa recibe la promoción por cada equipo vendido. */
    @Column(name = "chips_por_equipo", nullable = false)
    private Integer chipsPorEquipo;

    @Column(name = "micas_por_equipo", nullable = false)
    private Integer micasPorEquipo;

    @Column(name = "protectores_por_equipo", nullable = false)
    private Integer protectoresPorEquipo;
}
