package com.skycel.backend.dto.equipo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una unidad de equipo (celular o tablet) con su artículo, su sucursal, su precio y su estado. */
@Data
@Builder
public class EquipoResponseDto {
    private Long idUnidad;
    /** El IMEI o número de serie: es lo que identifica al equipo. */
    private String imei;
    /** IMEI o SERIE. */
    private String tipoIdentificador;

    private Integer idprodmaster;
    /** Nombre completo del artículo (categorías + nombre + color). */
    private String nombreArticulo;
    /** CELULAR o TABLET. */
    private String tipo;
    private Short idCategoria;
    private String categoriaPrincipal;
    private String subcategoria1;
    private String subcategoria2;
    private String nombreProducto;
    private String colorArticulo;
    private Integer diasGarantia;

    private Integer codti;
    private String nombreTienda;

    /** La sección del local donde está (null = sin asignar). */
    private Short idSeccion;
    private String nombreSeccion;

    /** DISPONIBLE, EN_GARANTIA, VENDIDO, TRASPASADO (en camino), DEVUELTO, DEBAJA. */
    private String estado;
    private String condicion;
    private BigDecimal precioVenta;
    /** Descuento automático vigente ahora y precio ya con él (lo que se cobra). */
    private BigDecimal descuentoAplicado;
    private BigDecimal precioFinal;
    /** Se omite (null) para quien no puede ver los costos. */
    private BigDecimal costo;
    private Short idProveedor;
    private String nombreProveedor;
    private Boolean rezagado;
    private LocalDateTime fechaRegistro;
    /** Días que lleva en existencia desde que se registró. */
    private Long diasEnExistencia;
}
