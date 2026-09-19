package com.skycel.backend.domain.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductoResponseDTO {
    private Integer idproducto;
    private String  codpro;
    private Integer codti;                // ← tienda donde está el producto
    private String  nombreTienda;         // ← nombre de la tienda
    private BigDecimal stock;
    private BigDecimal preciopro;         // precio compra
    private BigDecimal preciopub;         // precio venta
    private CatalogoSimpleResponseDTO color;
    private MagnitudResponseDTO magnitud;
    private ProveedorResponseDTO proveedor;
    private SeccionResponseDTO  seccion;
    private Integer idProductoMaster;
    private String  nombreProductoMaster;
    private String  marca;                // solo EQUIPO
    private String  modelo;               // solo EQUIPO
    private String  descripcion;          // "especificaciones" — ACCESORIO/SERVICIO
    private String  descripcion2;         // "notaAdicional" — solo SERVICIO
    private String  nombreCategoria;      // ← categoría del maestro
    private String  tipo;                 // Tipo de producto (CELULAR, ACCESORIO, SERVICIO)
    private java.util.List<ImeiInfoDTO> imeisDisponibles; // IMEIs disponibles con su precio (solo celulares)
    private Boolean activo;
}