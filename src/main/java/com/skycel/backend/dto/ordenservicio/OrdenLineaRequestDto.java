package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/** Un servicio o una refacción de la orden. */
@Data
public class OrdenLineaRequestDto {

    /** Código del producto en la tienda de la orden: un servicio (SRV-000001...) o un accesorio usado como refacción. */
    @NotBlank(message = "El código del producto es obligatorio")
    private String codpro;

    /** Por defecto 1. */
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Short cantidad;

    /** Por defecto, el precio de venta del producto. */
    @PositiveOrZero(message = "El precio no puede ser negativo")
    private BigDecimal precioUnitario;

    /** Regalo ($0 autorizado), p. ej. "incluye mica gratis". Solo un encargado o administrador lo puede autorizar. */
    private Boolean esRegalo;
}
