package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un gasto del negocio (renta, gasolina, herramienta, sueldos fuera del sistema...). Un gasto recurrente (la renta) se
 * cuenta solo en cada mes desde el mes de su fecha hasta el mes de {@code hastaFecha} (o sin fin si está vacía).
 */
@Entity
@Table(name = "gasto")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idgasto")
    private Integer idgasto;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "concepto", nullable = false, length = 150)
    private String concepto;

    /** Código de la categoría (ver FinanzasTallerService.CATEGORIAS). */
    @Column(name = "categoria", nullable = false, length = 30)
    private String categoria;

    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** La sucursal a la que corresponde; vacío = del negocio en general. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti")
    private Tienda tienda;

    @Column(name = "recurrente", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean recurrente;

    /** Último mes en que se cuenta un gasto recurrente; vacío = sigue contando cada mes. */
    @Column(name = "hasta_fecha")
    private LocalDate hastaFecha;

    @Column(name = "activo", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean activo;

    @Column(name = "creado_por", length = 100)
    private String creadoPor;

    @Column(name = "creado_el")
    private LocalDateTime creadoEl;

    @PrePersist
    void alCrear() {
        if (recurrente == null) recurrente = false;
        if (activo == null) activo = true;
        if (creadoEl == null) creadoEl = LocalDateTime.now();
    }
}
