package com.skycel.backend.dto.traspaso;

import lombok.Data;

import java.util.List;

/** Al aceptar una solicitud, quien surte elige qué unidades manda de cada equipo pedido. */
@Data
public class AceptarSolicitudRequestDto {

    /** Un renglón por cada equipo pedido, con los IMEI que se envían. Los accesorios no necesitan renglón. */
    private List<UnidadesElegidasDto> equipos;

    @Data
    public static class UnidadesElegidasDto {
        /** El artículo del equipo pedido (el equipo no lleva código). */
        private Integer idprodmaster;
        private String codpro;
        private List<String> imeis;
    }
}
