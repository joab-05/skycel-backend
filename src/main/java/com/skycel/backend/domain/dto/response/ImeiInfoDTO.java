package com.skycel.backend.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImeiInfoDTO {
    private String imei;
    private BigDecimal precioVenta;   // override si existe, si no el precio del modelo
    private Boolean tienePrecioPropio; // true si este IMEI tiene un override distinto al del modelo
    private String condicion;          // NUEVO, USADO o REACONDICIONADO
    private BigDecimal costoUnitario;  // costo propio de la unidad si lo tiene, si no el costo del modelo
    private Boolean tieneCostoPropio;  // true si la unidad tiene un costo distinto al del modelo
}
