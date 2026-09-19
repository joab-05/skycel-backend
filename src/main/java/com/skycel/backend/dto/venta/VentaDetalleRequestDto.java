package com.skycel.backend.dto.venta;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VentaDetalleRequestDto {

    @NotNull(message = "El idprodmaster es obligatorio")
    private Integer idprodmaster;

    /** Código específico de producto (puede ser null si se vende por master) */
    private String codpro;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Short cantidad;

    @NotNull(message = "El precio unitario final es obligatorio")
    @PositiveOrZero(message = "El precio no puede ser negativo")
    private BigDecimal precioUnitarioFinal;

    /** IMEI del equipo, si aplica */
    private String imei;

    /** ¿El artículo es un regalo (precio 0 autorizado)? */
    private Boolean esRegalo = false;
}