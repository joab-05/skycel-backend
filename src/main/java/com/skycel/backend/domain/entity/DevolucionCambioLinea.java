package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Un producto que el cliente se lleva a cambio de lo devuelto. Se guarda hasta que se aprueba la devolución. */
@Entity
@Table(name = "devolucion_cambio_linea")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DevolucionCambioLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idlinea")
    private Integer idlinea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iddevolucion", nullable = false)
    private Devolucion devolucion;

    @Column(name = "idprodmaster", nullable = false)
    private Integer idprodmaster;

    @Column(name = "codpro", length = 25)
    private String codpro;

    @Column(name = "nombre_producto", length = 255)
    private String nombreProducto;

    @Column(name = "imei", length = 20)
    private String imei;

    @Column(name = "cantidad", nullable = false)
    private Short cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;
}
