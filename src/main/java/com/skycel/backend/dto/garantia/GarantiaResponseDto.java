package com.skycel.backend.dto.garantia;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GarantiaResponseDto {

    private Integer idgarantia;
    private String  folio;
    private Integer idventa;

    // ── Producto ──────────────────────────────────────────────────────────────
    private String codpro;
    private String nombreProducto;
    /** CELULAR (equipo) o ACCESORIO */
    private String tipoProducto;
    private String imei;
    private LocalDate fechaVentaOriginal;
    /** Hasta cuándo cubría la garantía del producto. */
    private LocalDate garantiaVigenteHasta;

    // ── Recepción ─────────────────────────────────────────────────────────────
    private Integer codti;
    private String  nombreTienda;
    private Integer idusuarioRecibe;
    private String  nombreRecibe;
    private String  nombreContacto;
    private String  telefonoContacto;
    private String  fallaReportada;
    private String  diagnosticoInicial;
    private String  proveedor;

    // ── Seguimiento ───────────────────────────────────────────────────────────
    /** 1 a 11 (ver GarantiaService) */
    private Byte   estado;
    private String estadoDisplay;
    private LocalDateTime fechaIngreso;
    /** Ingreso + 30 días hábiles. */
    private LocalDate fechaLimiteSolucion;
    /** Días naturales que faltan para la fecha límite (negativo si ya pasó); null si ya se entregó. */
    private Long    diasRestantes;
    /** true si ya pasó la fecha límite y aún no se entrega. */
    private Boolean vencida;
    private String  imeiReemplazo;

    private List<HistorialDto> historial;

    @Data
    @Builder
    public static class HistorialDto {
        private Byte          estado;
        private String        estadoDisplay;
        private String        comentario;
        private String        nombreUsuario;
        private LocalDateTime fecha;
    }
}
