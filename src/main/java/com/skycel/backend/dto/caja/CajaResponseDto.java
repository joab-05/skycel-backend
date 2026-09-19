package com.skycel.backend.dto.caja;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CajaResponseDto {
    private Integer idCaja;
    private String  nombreCaja;
    private Integer codti;
    private String  nombreTienda;
    private Boolean esCajaPrincipal;
}