package com.skycel.backend.dto.servicios;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CorteServiciosResponseDto {
    private Integer idcorte;
    private Integer codti;
    private String  nombreTienda;
    private LocalDate fecha;
    /** 1 Capturado, 2 Confirmado, 3 Confirmado con diferencias */
    private Byte    estado;
    private String  estadoDisplay;
    private String  nombreCaptura;
    private LocalDateTime fechaCaptura;
    private String  observaciones;
    private String  nombreConfirma;
    private LocalDateTime fechaConfirmacion;
    private String  comentarioConfirmacion;
    private BigDecimal totalMonto;
    private BigDecimal totalComision;
    /** Suma de lo que reporta el proveedor (null mientras no se confirme). */
    private BigDecimal totalProveedor;
    /** Suma de las diferencias (monto - proveedor). */
    private BigDecimal totalDiferencia;
    private List<LineaDto> lineas;

    @Data
    @Builder
    public static class LineaDto {
        private Integer idlinea;
        private Integer idtipo;
        private String  nombre;
        private BigDecimal comisionPorOperacion;
        private Integer operaciones;
        private BigDecimal monto;
        private BigDecimal comision;
        private BigDecimal saldoInicial;
        private BigDecimal fondeo;
        private BigDecimal saldoFinal;
        /** saldoInicial + fondeo - saldoFinal: lo que bajó el saldo del proveedor (solo si se capturaron los tres). */
        private BigDecimal consumoSaldo;
        /** consumoSaldo - monto: si el saldo bajó distinto a lo que se dice que se operó. */
        private BigDecimal diferenciaSaldo;
        private BigDecimal montoProveedor;
        /** monto - montoProveedor (solo después de confirmar). */
        private BigDecimal diferencia;
    }
}
