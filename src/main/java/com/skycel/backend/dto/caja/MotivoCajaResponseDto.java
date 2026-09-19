package com.skycel.backend.dto.caja;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MotivoCajaResponseDto {
    private Integer idmotivo;
    private String  nombre;
    private Byte    tipoMov;
    private String  tipoMovDisplay;
    private Byte    catSat;
    private String  catSatDisplay;
    private Boolean activo;
    /** true = motivo que usa el sistema de forma automática (no se puede desactivar) */
    private Boolean delSistema;
}
