package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PATCH /api/productos/master/{id}
 * Atributos del artículo que no dependen de la tienda. Los campos que no se envían no cambian.
 */
@Data
public class ProductoMasterUpdateDTO {

    /** Nuevo nombre del artículo (cambia en todas las sucursales). No puede repetir el de otro artículo activo. */
    @Size(max = 255, message = "El nombre no puede exceder los 255 caracteres")
    private String nombreBase;

    /** Nuevo último eslabón del nombre (la variante). Si cambia él o la categoría y no se manda nombreBase, el nombre completo se rearma. */
    @Size(max = 120, message = "El nombre del producto no puede exceder los 120 caracteres")
    private String nombreProducto;

    /** Nueva categoría (del mismo tipo de producto que el artículo). */
    private Short idCategoria;

    @Size(max = 255, message = "La compatibilidad no puede exceder los 255 caracteres")
    private String compatibilidad;

    @Min(value = 1, message = "El tiempo estimado debe ser de al menos 1 minuto")
    private Integer tiempoEstimadoMin;

    @Min(value = 0, message = "Los días de garantía no pueden ser negativos")
    private Integer diasGarantia;
}
