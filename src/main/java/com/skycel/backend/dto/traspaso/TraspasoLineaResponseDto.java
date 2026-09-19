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
}
