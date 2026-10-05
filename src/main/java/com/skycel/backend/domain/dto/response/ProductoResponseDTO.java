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
    private BigDecimal stockMinimo;       // umbral de reposición (0 = sin alerta)
    private Boolean bajoStock;            // true si stock <= stockMinimo (no aplica a servicios)
    private BigDecimal preciopro;         // precio compra
    private BigDecimal preciopub;         // precio venta (de lista, sin descuento)
    private BigDecimal descuentoAplicado; // descuento automático vigente ahora mismo (0 si ninguno aplica)
    private BigDecimal precioFinal;       // lo que de verdad se cobra: preciopub - descuentoAplicado
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
    private String  compatibilidad;       // modelos con los que sirve — ACCESORIO/SERVICIO
    private Integer tiempoEstimadoMin;    // duración estimada en minutos — solo SERVICIO
    private Integer diasGarantia;         // días de garantía del artículo o servicio
    private String  nombreCategoria;      // ← categoría del maestro (la más profunda del árbol)
    private Short   idCategoria;          // id de esa categoría
    private String  nombreProducto;       // la descripción del producto (sin el color), si la tiene
    private Integer idDescripcion;        // id de la descripción en el catálogo
    private Short   idColorArticulo;      // color del artículo (parte de su nombre completo), si lo tiene
    private String  colorArticulo;
    private Short   idCategoriaPrincipal; // categoría principal del árbol (nivel 1)
    private String  categoriaPrincipal;
    private String  subcategoria1;        // nivel 2 (define el prefijo del código)
    private String  subcategoria2;        // nivel 3 (opcional)
    private String  tipo;                 // Tipo de producto (CELULAR, ACCESORIO, SERVICIO)
    private java.util.List<ImeiInfoDTO> imeisDisponibles; // IMEIs disponibles con su precio (solo celulares)
    private Boolean activo;
}