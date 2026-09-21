package com.skycel.backend.dto.venta;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VentaResponseDto {

    private Integer idventa;
    private Integer codti;
    private String nombreTienda;
    private Integer idCaja;
    private String nombreCaja;

    // ── Cliente ───────────────────────────────────────────────────────────────
    private Integer idcliente;
    private String nombreCliente;
    private String telefonoCliente;

    // ── Vendedor ──────────────────────────────────────────────────────────────
    private String usernameVendedor;
    /** Folio provisional del ticket impreso sin conexión (si la venta se hizo sin conexión). */
    private String folioLocal;
    private String nombreVendedor;

    // ── Comprobante ───────────────────────────────────────────────────────────
    private Byte tipoComprobante;
    private String descripcionComprobante;  // "Ticket" / "Factura"
    private Byte metodoPago;
    private String descripcionMetodoPago;   // "Efectivo" / "Tarjeta" / etc.
    private Byte estado;
    private String descripcionEstado;       // "Completada" / "Cancelada"

    // ── Totales ───────────────────────────────────────────────────────────────
    private BigDecimal total;
    private BigDecimal montoAbonado;
    private BigDecimal cambio;

    private String observaciones;
    private LocalDateTime fechaVenta;

    // ── Líneas ────────────────────────────────────────────────────────────────
    private List<VentaDetalleResponseDto> detalles;
    /** Desglose del pago (solo ventas con método Mixto; vacío en las demás). */
    private List<VentaPagoDetalleResponseDto> pagos;
}