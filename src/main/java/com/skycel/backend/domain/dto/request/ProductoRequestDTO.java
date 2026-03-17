package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductoRequestDTO {

    @NotBlank(message = "El código de producto (codpro) es obligatorio")
    @Size(max = 25)
    private String codpro;

    @NotNull(message = "El stock inicial es obligatorio")
    private BigDecimal stock;

    @NotNull(message = "El precio de compra es obligatorio")
    private BigDecimal precioCompra;

    @NotNull(message = "El precio de venta es obligatorio")
    private BigDecimal precioVenta;

    @NotNull(message = "El color es obligatorio")
    private Short idColor;

    @NotNull(message = "La magnitud/unidad es obligatoria")
    private Short idMagnitud;

    @NotNull(message = "El proveedor es obligatorio")
    private Short idProveedor;

    @NotNull(message = "La sección/ubicación es obligatoria")
    private Short idSeccion;

    @NotNull(message = "ID de producto maestro es obligatorio")
    private Long idProductoMaster;
}
