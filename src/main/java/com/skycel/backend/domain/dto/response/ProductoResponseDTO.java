package com.skycel.backend.domain.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductoResponseDTO {
    private Integer idproducto;
    private String codpro;
    private BigDecimal stock;
    private BigDecimal preciopro; // Precio compra
    private BigDecimal preciopub; // Precio venta
    private CatalogoSimpleResponseDTO color;
    private MagnitudResponseDTO magnitud;
    private ProveedorResponseDTO proveedor;
    private SeccionResponseDTO seccion;
    private Integer idProductoMaster;
    private String nombreProductoMaster;
}
