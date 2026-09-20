package com.skycel.backend.dto.devolucion;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DevolucionResponseDto {
    private Integer iddevolucion;
    private String  folio;
    private Integer idventa;
    private Integer codti;
    private String  nombreTienda;
    private String  nombreCliente;
    /** 1 Reembolso, 2 Cambio */
    private Byte    tipo;
    private String  tipoDisplay;
    /** 1 Pendiente, 2 Procesada, 3 Rechazada */
    private Byte    estado;
    private String  estadoDisplay;
    private String  motivo;
    private BigDecimal totalDevuelto;
    private Byte    metodoReembolso;
    private String  descripcionMetodoReembolso;
    /** Cambio: lo que vale lo nuevo y lo que paga el cliente de diferencia. */
    private BigDecimal totalCambio;
    private BigDecimal diferencia;
    private Byte    metodoDiferencia;
    private Integer idventaCambio;
    private String  nombreSolicita;
    private String  nombreResuelve;
    private String  comentarioResolucion;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaResolucion;
    private List<LineaDto> lineas;
    private List<CambioLineaDto> cambio;

    @Data
    @Builder
    public static class LineaDto {
        private Integer    iddetalleVenta;
        private String     codpro;
        private String     nombreProducto;
        private String     imei;
        private Short      cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
        private Boolean    reingresa;
    }

    @Data
    @Builder
    public static class CambioLineaDto {
        private String     codpro;
        private String     nombreProducto;
        private String     imei;
        private Short      cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
    }
}
