package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Tasas de comisión por venta, compartidas por todas las tiendas (una sola fila, id = 1). */
@Entity
@Table(name = "config_comision")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfigComision {

    public static final int ID_UNICO = 1;

    @Id
    @Column(name = "id")
    private Integer id;

    /** % mensual que gana el encargado de tienda sobre las ventas de su sucursal (Equipo/Accesorio, sin PayJoy). */
    @Column(name = "tasa_encargado_mensual", nullable = false, precision = 5, scale = 2)
    private BigDecimal tasaEncargadoMensual;

    /** Precio del equipo a partir del cual la venta PayJoy pasa de la tasa baja a la alta. */
    @Column(name = "payjoy_umbral", nullable = false, precision = 12, scale = 2)
    private BigDecimal payjoyUmbral;

    /** % de comisión PayJoy para un equipo con precio MENOR al umbral. */
    @Column(name = "payjoy_tasa_baja", nullable = false, precision = 5, scale = 2)
    private BigDecimal payjoyTasaBaja;

    /** % de comisión PayJoy para un equipo con precio IGUAL O MAYOR al umbral. */
    @Column(name = "payjoy_tasa_alta", nullable = false, precision = 5, scale = 2)
    private BigDecimal payjoyTasaAlta;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;
}
