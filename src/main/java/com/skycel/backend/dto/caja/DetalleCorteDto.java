package com.skycel.backend.dto.caja;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El detalle de un turno de caja para auditarlo: cada venta con lo que se llevó el cliente (equipos con su IMEI y si fueron al contado
 * o con PayJoy) y cada movimiento de efectivo con su motivo, nota y quién lo registró.
 */
@Data
@Builder
public class DetalleCorteDto {

    private Integer idsesionCaja;
    private Integer idCaja;
    private String  nombreCaja;
    private Integer codti;
    private String  nombreTienda;
    private boolean abierta;
    private LocalDateTime desde;
    private LocalDateTime hasta;

    private List<VentaDto> ventas;
    private List<MovimientoDto> movimientos;
    private EquiposDto equipos;
    /** Cuántas ventas completadas traen una alerta de efectivo. */
    private int ventasConAlerta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VentaDto {
        private Integer idventa;
        private LocalDateTime fecha;
        private String vendedor;
        private String cliente;
        /** Efectivo, Transferencia, Mixto, PayJoy... */
        private String metodo;
        private BigDecimal total;
        /** false = cancelada: aparece para que se vea, pero no suma. */
        private boolean completada;
        /** true si la venta se hizo sin conexión y llegó después. */
        private boolean sinConexion;
        private String observaciones;
        private List<ArticuloDto> articulos;
        /** Solo en pagos mixtos (y cualquier venta con desglose): cómo se pagó cada parte. */
        private List<PagoDto> pagos;
        /** Efectivo que esta venta dejó registrado en la caja (la entrada «Venta #n» del cajón). */
        private BigDecimal efectivoEnCaja;
        /** Si el efectivo registrado no coincide con lo que la venta debió dejar, qué pasó; vacío si todo cuadra o no se puede saber (PayJoy). */
        private String alerta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArticuloDto {
        private String codigo;
        private String nombre;
        private int cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal importe;
        /** Solo equipos. */
        private String imei;
        private boolean equipo;
        /** true si el equipo se vendió a crédito con PayJoy. */
        private boolean payjoy;
        private BigDecimal enganche;
        private Integer plazoMeses;
        private boolean regalo;
        /** Promoción de equipo aplicada (CHIP, MICA, PROTECTOR, R12...), si la hubo. */
        private String promocion;
        /** true si el precio lo cambió un administrador con una solicitud de descuento. */
        private boolean precioAutorizado;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagoDto {
        private String metodo;
        private BigDecimal monto;
        private String folio;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovimientoDto {
        private Integer idmovimiento;
        private LocalDateTime fecha;
        /** 1 entrada, 2 salida. */
        private int tipo;
        private String motivo;
        /** VENTA, ABONO, TURNO (fondo, ajuste y retiro del cierre) u OTRO (lo que registró una persona). */
        private String origen;
        private BigDecimal monto;
        private String usuario;
        private String observaciones;
    }

    /** Cuántos equipos se vendieron en el turno y cómo (para ver de un vistazo que lo que se vendió al contado entró a la caja). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquiposDto {
        private int contado;
        private int payjoy;
        private BigDecimal importeContado;
        private BigDecimal enganchesPayjoy;
    }
}
