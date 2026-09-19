package com.skycel.backend.dto.traspaso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Un renglón de un traspaso: el producto, cuántas unidades y, en equipos, cuáles (IMEI o serie). */
@Data
public class TraspasoLineaRequestDto {

    /** Código del producto. En un envío, el de la tienda origen; en una solicitud, el de la tienda que surte. */
    @NotBlank(message = "El código del producto es obligatorio")
    private String codpro;

    @NotNull(message = "La cantidad es obligatoria")
    @Positive(message = "La cantidad debe ser mayor a 0")
    private BigDecimal cantidad;

    /** Solo en un ENVIO de equipos (celular o tablet): los IMEI o números de serie que viajan (uno por unidad). */
    private List<String> imeis;
}
