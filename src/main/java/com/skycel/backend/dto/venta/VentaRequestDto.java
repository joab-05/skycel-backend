package com.skycel.backend.dto.venta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class VentaRequestDto {

    @NotNull(message = "La tienda es obligatoria")
    private Integer codti;

    @NotNull(message = "La caja es obligatoria")
    private Integer idCaja;

    /** Cliente opcional (venta de mostrador sin cliente registrado = null) */
    private Integer idcliente;

    /**
     * Tipo de comprobante:
     * 1 = Ticket, 2 = Factura
     */
    @NotNull
    private Byte tipoComprobante = 1;

    /**
     * Método de pago:
     * 1 = Efectivo, 2 = Tarjeta, 3 = Transferencia, 4 = Mixto, 5 = PayJoy
     */
    @NotNull(message = "El método de pago es obligatorio")
    private Byte metodoPago;

    /** Monto entregado por el cliente (para calcular cambio) */
    private BigDecimal montoAbonado;

    private String observaciones;

    /**
     * Solo aplica a ventas a crédito (montoAbonado menor al total): fecha límite para liquidar
     * el saldo. Si no se indica, son 30 días desde hoy.
     */
    private LocalDate fechaVencimientoCredito;

    /**
     * Desglose del pago. Obligatorio (2 líneas o más) cuando metodoPago = 4 (Mixto) y no se admite con
     * otros métodos. El monto abonado es la suma de las líneas; si es menor al total, es una venta a crédito.
     */
    @Valid
    private List<VentaPagoDetalleRequestDto> pagos;

    // ── Ventas hechas sin conexión (las envía el equipo cuando vuelve el internet) ─────────────────────────

    /**
     * UUID de la venta que genera el equipo. Si ya se registró con esta clave se devuelve la misma venta (no se duplica),
     * así que un envío que se repite (p. ej. se cortó la respuesta) es seguro. Lo mandan todas las ventas del POS.
     */
    @jakarta.validation.constraints.Size(max = 64, message = "La clave no puede exceder los 64 caracteres")
    private String claveOffline;

    /** Folio provisional del ticket impreso sin conexión (OFF-...). */
    @jakarta.validation.constraints.Size(max = 40, message = "El folio local no puede exceder los 40 caracteres")
    private String folioLocal;

    /**
     * true = la venta se hizo sin conexión y se envía después. Solo entonces se conserva la fecha en que ocurrió y no se rechaza
     * por stock insuficiente (ya sucedió; el encargado revisa las diferencias).
     */
    private Boolean sinConexion;

    /** Cuándo ocurrió la venta (solo si sinConexion; no puede ser futura ni de hace más de 45 días). */
    private java.time.LocalDateTime fechaVenta;

    /** Quién vendió (solo si sinConexion). Lo puede indicar quien envía si es él mismo o un encargado o administrador. */
    private String usernameVendedor;

    /** Cliente que no estaba registrado al vender sin conexión: se busca por teléfono y, si no existe, se crea. */
    @jakarta.validation.constraints.Size(max = 150)
    private String clienteNombre;
    @jakarta.validation.constraints.Size(max = 15)
    private String clienteTelefono;

    @NotEmpty(message = "La venta debe tener al menos un artículo")
    @Valid
    private List<VentaDetalleRequestDto> detalles;
}