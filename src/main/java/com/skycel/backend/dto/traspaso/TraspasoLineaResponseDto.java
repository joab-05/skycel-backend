package com.skycel.backend.dto.traspaso;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class TraspasoLineaResponseDto {
    private String codpro;
    private String nombreProducto;
    /** CELULAR (equipo), ACCESORIO o SERVICIO */
    private String tipoProducto;
    private BigDecimal cantidad;
    /** Solo equipos en un envío: las unidades que viajan. */
    private List<String> imeis;
    /** Lo que llegó (null mientras el envío no se recibe). */
    private BigDecimal cantidadRecibida;
    /** Lo que falta por resolver de este renglón. */
    private BigDecimal faltantePendiente;
}
