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

    /** Código del producto (accesorios). En un envío, el de la tienda origen; en una solicitud, el de la tienda que surte. Un equipo no lleva código. */
    private String codpro;

    /** Solo en la SOLICITUD de equipos: el artículo que se pide (con su cantidad); el envío lo toma de cada IMEI. */
    private Integer idprodmaster;

    /** Accesorios y solicitudes de equipos; en un envío de equipos se toma de los IMEI. */
    @Positive(message = "La cantidad debe ser mayor a 0")
    private BigDecimal cantidad;

    /** Solo en un ENVIO de equipos (celular o tablet): los IMEI o números de serie que viajan (uno por unidad). */
    private List<String> imeis;
}
