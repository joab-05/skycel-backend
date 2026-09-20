package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Datos del negocio compartidos por todas las tiendas (una sola fila, id = 1): los del ticket y los plazos. El domicilio
 * y el teléfono de cada sucursal están en la tienda.
 */
@Entity
@Table(name = "configuracion_negocio")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfiguracionNegocio {

    public static final int ID_UNICO = 1;

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "nombre_comercial", nullable = false, length = 120)
    private String nombreComercial;

    @Column(name = "razon_social", length = 150)
    private String razonSocial;

    @Column(name = "rfc", length = 13)
    private String rfc;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "sitio_web", length = 120)
    private String sitioWeb;

    /** Texto al pie del ticket (agradecimiento, políticas...). Varias líneas separadas por salto de línea. */
    @Column(name = "ticket_pie", length = 255)
    private String ticketPie;

    /** Leyenda fiscal o de garantía que se imprime debajo del pie. */
    @Column(name = "ticket_leyenda", length = 255)
    private String ticketLeyenda;

    /** Días naturales después de la venta durante los que se admite una devolución. */
    @Column(name = "dias_devolucion", nullable = false)
    private Integer diasDevolucion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;
}
