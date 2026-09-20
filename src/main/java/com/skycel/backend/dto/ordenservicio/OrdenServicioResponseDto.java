package com.skycel.backend.dto.ordenservicio;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrdenServicioResponseDto {

    private Integer idorden;
    private String  folio;
    private Integer codti;
    private String  nombreTienda;

    private Integer idcliente;
    private String  nombreCliente;
    private String  telefonoCliente;

    private Integer idusuarioRecibe;
    private String  nombreRecibe;
    private Integer idTecnico;
    private String  nombreTecnico;

    // ── Equipo ────────────────────────────────────────────────────────────────
    private String marca;
    private String modelo;
    private String imei;
    private String accesoriosDejados;
    private String estadoFisico;
    private String fallaReportada;
    private String diagnostico;

    /** 1 Recibida, 2 En reparación, 3 Lista para entrega, 4 Entregada, 5 Cancelada */
    private Byte   estado;
    private String estadoDisplay;

    private LocalDateTime fechaIngreso;
    private LocalDate     fechaPromesa;
    private LocalDateTime fechaLista;
    private LocalDateTime fechaEntrega;
    private Integer       diasGarantia;
    private LocalDate     fechaGarantiaHasta;

    /** La venta generada al entregar. */
    private Integer idventa;
    private String  motivoCancelacion;

    // ── Dinero ────────────────────────────────────────────────────────────────
    private BigDecimal total;
    private BigDecimal totalAnticipos;
    /** Lo que falta por cobrar (total - anticipos, nunca negativo). */
    private BigDecimal saldo;

    private List<OrdenLineaResponseDto> lineas;
    private List<AnticipoResponseDto>   anticipos;
    private List<HistorialResponseDto>  historial;

    @Data
    @Builder
    public static class OrdenLineaResponseDto {
        private Integer    iddetalle;
        private String     codpro;
        private String     nombre;
        /** SERVICIO o ACCESORIO (refacción) */
        private String     tipoProducto;
        private Short      cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
        private Boolean    esRegalo;
    }

    @Data
    @Builder
    public static class AnticipoResponseDto {
        private Integer       idanticipo;
        private BigDecimal    monto;
        private Byte          metodoPago;
        private String        descripcionMetodoPago;
        private String        folioOperacion;
        private String        nombreUsuario;
        private LocalDateTime fecha;
        /** El movimiento de caja (solo anticipos en efectivo). */
        private Integer       idmovimientoCaja;
    }

    @Data
    @Builder
    public static class HistorialResponseDto {
        private Byte          estado;
        private String        estadoDisplay;
        private String        comentario;
        private String        nombreUsuario;
        private LocalDateTime fecha;
    }
}
