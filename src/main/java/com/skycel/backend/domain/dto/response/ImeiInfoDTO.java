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
}
