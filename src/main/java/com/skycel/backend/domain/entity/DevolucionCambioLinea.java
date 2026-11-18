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

    /** El precio al que se entrega el producto nuevo (el que se cobra en la venta del cambio). */
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    /**
     * Lo que cuesta hoy ese producto en el sistema (precio de lista menos el descuento automático vigente), calculado por el
     * servidor al solicitar. Si {@link #precioUnitario} es menor, el cambio se entrega a precio especial y solo lo aprueba un
     * administrador. Nulo en las devoluciones anteriores a esta regla o si no se pudo consultar.
     */
    @Column(name = "precio_lista", precision = 12, scale = 2)
    private BigDecimal precioLista;

    /** ¿Se entrega por menos de lo que cuesta en el sistema? */
    public boolean esPrecioEspecial() {
        return precioLista != null && precioUnitario != null && precioUnitario.compareTo(precioLista) < 0;
    }
}
