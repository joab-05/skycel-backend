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
    private String  folioLocal;
    private Integer codti;
    private String  nombreTienda;

    private Integer idcliente;
    private String  nombreCliente;
    private String  telefonoCliente;

    private Integer idusuarioRecibe;
    private String  nombreRecibe;
    private Integer idTecnico;
    private String  nombreTecnico;
    /** La sucursal del técnico asignado (el taller): de su inventario salen las refacciones. Null mientras no hay técnico. */
    private Integer codtiTaller;
    private String  nombreTaller;

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
    private Integer       horasEstimadas;
    private java.time.LocalDateTime fechaPromesaHora;
    private LocalDateTime fechaLista;
    /** Cuándo regresó el equipo del taller a la sucursal que cobra (null si sigue en el taller). */
    private LocalDateTime fechaRegresoTienda;
    /** Está lista pero sigue en el taller: no se avisa al cliente ni se cobra hasta que regrese a la sucursal. */
    private Boolean regresoPendiente;
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

    // ── Autorización del cliente a la cotización ──────────────────────────────
    /** null = sin cotizar; 1 = esperando respuesta del cliente; 2 = autorizó; 3 = no autorizó. */
    private Byte       autorizacionEstado;
    private String     autorizacionDisplay;
    /** El total cotizado o autorizado; si difiere de {@code total}, el precio cambió y falta avisar. */
    private BigDecimal autorizacionTotal;
    private BigDecimal autorizacionTotalPrevio;
    private String     autorizacionMedio;
    private String     autorizacionNota;
    private String     autorizacionPor;
    private LocalDateTime autorizacionFecha;

    // ── Espera de refacción ───────────────────────────────────────────────────
    /** Qué se espera; null = la reparación no está detenida. */
    private String        esperaMotivo;
    private LocalDateTime esperaDesde;
    /** Minutos que la orden ya estuvo detenida en esperas anteriores ya terminadas. */
    private Integer       esperaMinutos;

    // ── Equipo listo sin recoger ──────────────────────────────────────────────
    /** Días que lleva lista sin que la recojan (solo estado Lista). */
    private Integer       diasListaSinRecoger;
    private LocalDateTime ultimoRecordatorio;

    // ── Revisión y fotos al recibir ───────────────────────────────────────────
    /** Punto de la revisión → OK, FALLA o SIN_PROBAR. Vacío si no se hizo. */
    private java.util.Map<String, String> checklist;
    /** Solo en el detalle: en listados va null para no exponerlo. */
    private String        codigoDesbloqueo;
    private Integer       totalFotos;

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
        /** Lo que costó la refacción (por unidad). Solo lo trae el técnico encargado de la orden o quien tenga COSTOS_VER; para los demás, null. */
        private BigDecimal costoUnitario;
        private Boolean    esRegalo;
        /** De qué sucursal sale la refacción (el taller); null en un servicio o en una línea anterior. */
        private Integer    codtiStock;
        private String     nombreTiendaStock;
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
