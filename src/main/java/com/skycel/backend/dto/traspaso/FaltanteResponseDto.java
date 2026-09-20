package com.skycel.backend.dto.traspaso;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FaltanteResponseDto {
    private Integer iddetalle;
    private Integer idtraspaso;
    private Integer codtiOrigen;
    private String nombreTiendaOrigen;
    private Integer codtiDestino;
    private String nombreTiendaDestino;
    private String codpro;
    private String nombreProducto;
    /** Solo equipos. */
    private String imei;
    private BigDecimal cantidadEnviada;
    private BigDecimal cantidadRecibida;
    private BigDecimal cantidadBaja;
    private BigDecimal cantidadReintegrada;
    /** Lo que sigue sin resolverse. */
    private BigDecimal pendiente;
    private String comentarioRecepcion;
    private List<MovimientoDto> historial;

    @Data
    @Builder
    public static class MovimientoDto {
        private String accion;
        private BigDecimal cantidad;
        private String usuario;
        private String nota;
        private LocalDateTime fecha;
    }
}
