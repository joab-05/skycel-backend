package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Una línea de una compra: cuánto se recibió de un producto y a qué costo (por unidad). */
@Entity
@Table(name = "compra_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompraDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idlinea")
    private Integer idlinea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcompra", nullable = false)
    private Compra compra;

    /** El producto (accesorios). Vacío en una línea de equipos: esa se identifica por su artículo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproducto")
    private Producto producto;

    /** El artículo de una línea de equipos (celulares o tablets recibidos por IMEI). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idprodmaster")
    private ProductoMaster productoMaster;

    @Column(name = "cantidad", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "costo_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
