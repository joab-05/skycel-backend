package com.skycel.backend.dto.devolucion;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que se puede devolver de una venta: si está en plazo y, por renglón, cuánto queda por devolver. */
@Data
@Builder
public class VentaDevolvibleResponseDto {
    private Integer   idventa;
    private LocalDate fechaVenta;
    private Integer   codti;
    private String    nombreTienda;
    private String    nombreCliente;
    private String    descripcionMetodoPago;
    private BigDecimal total;
    /** false si la venta ya no admite devoluciones (cancelada, a crédito, fuera de plazo...). */
    private Boolean   elegible;
    /** Cuando no es elegible, la razón. */
    private String    motivo;
    private LocalDate devolucionHasta;
    private List<LineaDto> lineas;

    @Data
    @Builder
    public static class LineaDto {
        private Integer    iddetalleVenta;
        private String     codpro;
        private String     nombreProducto;
        /** CELULAR o ACCESORIO */
        private String     tipoProducto;
        private String     imei;
        private Short      cantidadVendida;
        private Long       cantidadYaDevuelta;
        private Long       disponible;
        private BigDecimal precioUnitario;
    }
}
