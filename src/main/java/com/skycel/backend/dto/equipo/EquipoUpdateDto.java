package com.skycel.backend.dto.equipo;

import lombok.Data;

import java.math.BigDecimal;

/** Cambios a un equipo: solo se modifica lo que viene. */
@Data
public class EquipoUpdateDto {
    /** Corrige el IMEI o número de serie (solo si el equipo está disponible, en baja o devuelto). */
    private String nuevoImei;
    /** Cambia el artículo al que pertenece la unidad (otra categoría, nombre o color). */
    private Integer idProductoMaster;
    private BigDecimal precioVenta;
    private BigDecimal costo;
    /** NUEVO, SEMINUEVO o REACONDICIONADO. */
    private String condicion;
    private Short idProveedor;
    private Short idSeccion;
    private Boolean rezagado;
}
