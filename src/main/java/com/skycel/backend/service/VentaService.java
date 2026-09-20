package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.venta.VentaDetalleRequestDto;
import com.skycel.backend.dto.venta.VentaDetalleResponseDto;
import com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto;
import com.skycel.backend.dto.venta.VentaPagoDetalleResponseDto;
import com.skycel.backend.dto.venta.VentaRequestDto;
import com.skycel.backend.dto.venta.VentaResponseDto;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Módulo de Ventas.
 *
 * Alcance de esta primera versión (decisiones acordadas):
 *  - Un CELULAR se vende con exactamente 1 IMEI por línea (cantidad = 1).
 *  - Si el monto abonado es menor al total (venta a crédito), se exige un
 *    cliente registrado y se genera una Cuenta por Cobrar por el saldo (folio V-######,
 *    vence a los 30 días salvo que se indique otra fecha). Al cancelar la venta se elimina
 *    esa cuenta; si ya tiene abonos, la cancelación se bloquea.
 *  - Movimientos de caja: una venta en EFECTIVO registra una entrada (lo abonado,
 *    sin exceder el total); cancelarla registra la salida que la revierte. Los demás
 *    métodos de pago no mueven la caja. Un pago MIXTO exige el desglose (pagos): el monto
 *    abonado es la suma de sus líneas y solo la parte en efectivo entra a la caja.
 *    Aún no se asocia la sesión de caja (idsesion).
 */
@Service
@RequiredArgsConstructor
public class VentaService {

    private static final byte ESTADO_COMPLETADA = 1;
    private static final byte ESTADO_CANCELADA  = 2;
    private static final byte METODO_EFECTIVO   = 1;
    private static final byte METODO_MIXTO      = 4;
    /** Solo en el desglose de una venta de una orden de servicio: lo que el cliente ya pagó como anticipo. */
    private static final byte METODO_ANTICIPO   = 6;
    /** Métodos admitidos en las líneas de un pago Mixto: efectivo, tarjeta, transferencia y PayJoy. */
    private static final Set<Byte> METODOS_DESGLOSE = Set.of((byte) 1, (byte) 2, (byte) 3, (byte) 5);

    private final VentaRepository          ventaRepository;
    private final VentaDetalleRepository   ventaDetalleRepository;
    private final ProductoRepository       productoRepository;
    private final ProductoMasterRepository productoMasterRepository;
    private final ProductoImeiRepository   productoImeiRepository;
    private final TiendaRepository         tiendaRepository;
    private final CajaRepository           cajaRepository;
    private final ClienteRepository        clienteRepository;
    private final UsuarioRepository        usuarioRepository;
    private final MovimientoCajaService    movimientoCajaService;
    private final CuentaPorCobrarService   cuentaPorCobrarService;
    private final VentaPagoDetalleRepository ventaPagoDetalleRepository;
    private final OrdenServicioRepository  ordenServicioRepository;

    // ── Crear venta ──────────────────────────────────────────────────────────

    @Transactional
    public VentaResponseDto crear(VentaRequestDto dto, Integer idUsuarioVendedor) {
        return crearVenta(dto, idUsuarioVendedor, false);
    }

    /**
     * Uso interno: la venta que genera una orden de servicio al entregar el equipo. A diferencia de una venta
     * normal, su desglose puede incluir una línea de pago ANTICIPO (método 6): lo que el cliente ya pagó al
     * dejar el equipo. Esa línea queda en el desglose pero no mueve la caja (el efectivo ya entró al recibir
     * el anticipo).
     */
    @Transactional
    public VentaResponseDto crearDesdeOrdenServicio(VentaRequestDto dto, Integer idUsuarioVendedor) {
        return crearVenta(dto, idUsuarioVendedor, true);
    }

    private VentaResponseDto crearVenta(VentaRequestDto dto, Integer idUsuarioVendedor, boolean desdeOrden) {

        Tienda tienda = tiendaRepository.findById(dto.getCodti())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tienda no encontrada: " + dto.getCodti()));

        Caja caja = cajaRepository.findById(dto.getIdCaja())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Caja no encontrada: " + dto.getIdCaja()));

        if (!caja.getTienda().getCodti().equals(tienda.getCodti())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La caja " + caja.getIdCaja() + " no pertenece a la tienda " + tienda.getCodti());
        }

        Usuario vendedor = usuarioRepository.findById(idUsuarioVendedor)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario vendedor no encontrado"));

        Cliente cliente = null;
        if (dto.getIdcliente() != null) {
            cliente = clienteRepository.findById(dto.getIdcliente())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente no encontrado"));
        }

        if (dto.getDetalles() == null || dto.getDetalles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta debe tener al menos un artículo.");
        }

        // 1) Resolver y validar cada línea SIN mutar nada todavía, para poder
        //    fallar rápido antes de tocar stock/IMEIs.
        List<LineaResuelta> lineas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (VentaDetalleRequestDto d : dto.getDetalles()) {
            LineaResuelta linea = resolverLinea(d, tienda, vendedor, desdeOrden);
            lineas.add(linea);
            total = total.add(linea.subtotal);
        }
        if (total.signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La venta no puede ser de $0: agregue al menos un artículo con precio.");
        }

        BigDecimal montoAbonado = resolverMontoAbonado(dto, total, desdeOrden);

        if (montoAbonado.compareTo(total) < 0 && cliente == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto abonado ($" + montoAbonado + ") es menor al total ($" + total +
                            "). Una venta a crédito requiere un cliente registrado.");
        }

        if (dto.getFechaVencimientoCredito() != null && dto.getFechaVencimientoCredito().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de vencimiento del crédito no puede ser anterior a hoy.");
        }

        // 2) Ya validado todo: persistir la venta.
        Venta venta = Venta.builder()
                .usuarioVendedor(vendedor)
                .cliente(cliente)
                .tienda(tienda)
                .caja(caja)
                .tipoComprobante(dto.getTipoComprobante() != null ? dto.getTipoComprobante() : (byte) 1)
                .metodoPago(dto.getMetodoPago())
                .estado(ESTADO_COMPLETADA)
                .total(total)
                .montoAbonado(montoAbonado)
                .observaciones(dto.getObservaciones())
                .build();
        venta = ventaRepository.save(venta);
        guardarDesglose(venta, dto);

        // 3) Aplicar el descuento de stock/IMEI y guardar cada línea.
        List<VentaDetalle> detallesGuardados = new ArrayList<>();
        for (LineaResuelta linea : lineas) {
            aplicarDescuentoStock(linea);

            VentaDetalle detalle = VentaDetalle.builder()
                    .venta(venta)
                    .productoMaster(linea.productoMaster)
                    .codpro(linea.producto.getCodpro())
                    .cantidad(linea.cantidadSolicitada)
                    .precioUnitarioBase(linea.precioUnitarioBase)
                    .precioUnitarioFinal(linea.precioUnitarioFinal)
                    .costoUnitarioCompra(linea.costoUnitarioCompra)
                    .esRegalo(linea.esRegalo)
                    .imei(linea.imei)
                    .build();
            detallesGuardados.add(ventaDetalleRepository.save(detalle));
        }

        // 4) Solo el efectivo mueve la caja (tarjeta/transferencia/PayJoy no entran al cajón).
        if (venta.getMetodoPago() != null && venta.getMetodoPago() == METODO_EFECTIVO) {
            movimientoCajaService.registrarVentaEfectivo(venta, vendedor);
        } else if (venta.getMetodoPago() != null && venta.getMetodoPago() == METODO_MIXTO) {
            // En un pago Mixto solo la parte pagada en efectivo entra al cajón.
            movimientoCajaService.registrarEntradaVenta(venta, vendedor, efectivoDelDesglose(dto));
        }

        // 5) Venta a crédito: el saldo queda como cuenta por cobrar (el cliente es obligatorio, ya validado).
        if (montoAbonado.compareTo(total) < 0) {
            cuentaPorCobrarService.crearDesdeVenta(venta, total.subtract(montoAbonado), dto.getFechaVencimientoCredito());
        }

        return toResponseDto(venta, detallesGuardados);
    }

    // ── Pago Mixto ───────────────────────────────────────────────────────────

    /**
     * Monto abonado de la venta. En un pago Mixto es la suma del desglose (que no puede superar el total
     * ni contradecir un montoAbonado explícito); en los demás casos, lo indicado o el total.
     */
    private BigDecimal resolverMontoAbonado(VentaRequestDto dto, BigDecimal total, boolean desdeOrden) {
        boolean mixto = dto.getMetodoPago() != null && dto.getMetodoPago() == METODO_MIXTO;
        List<VentaPagoDetalleRequestDto> pagos = dto.getPagos();
        boolean hayPagos = pagos != null && !pagos.isEmpty();

        if (!mixto) {
            if (hayPagos) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El desglose de pagos solo aplica al método Mixto (4).");
            }
            return dto.getMontoAbonado() != null ? dto.getMontoAbonado() : total;
        }

        // Con anticipo basta una línea: puede ser que todo ya esté pagado.
        boolean conAnticipo = desdeOrden && hayPagos
                && pagos.stream().anyMatch(p -> p.getMetodoPago() != null && p.getMetodoPago() == METODO_ANTICIPO);
        if (!hayPagos || (pagos.size() < 2 && !conAnticipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un pago Mixto requiere el desglose (pagos) con al menos 2 líneas.");
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (VentaPagoDetalleRequestDto p : pagos) {
            boolean esAnticipo = desdeOrden && p.getMetodoPago() != null && p.getMetodoPago() == METODO_ANTICIPO;
            if (p.getMetodoPago() == null || (!esAnticipo && !METODOS_DESGLOSE.contains(p.getMetodoPago()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Método de pago no válido en el desglose: " + p.getMetodoPago()
                                + " (use 1 Efectivo, 2 Tarjeta, 3 Transferencia o 5 PayJoy).");
            }
            suma = suma.add(p.getMonto());
        }
        if (suma.compareTo(total) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El desglose ($" + suma + ") supera el total de la venta ($" + total + ").");
        }
        if (dto.getMontoAbonado() != null && dto.getMontoAbonado().compareTo(suma) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto abonado ($" + dto.getMontoAbonado() + ") no coincide con la suma del desglose ($" + suma + ").");
        }
        return suma;
    }

    private void guardarDesglose(Venta venta, VentaRequestDto dto) {
        if (venta.getMetodoPago() == null || venta.getMetodoPago() != METODO_MIXTO) return;
        for (VentaPagoDetalleRequestDto p : dto.getPagos()) {
            ventaPagoDetalleRepository.save(VentaPagoDetalle.builder()
                    .venta(venta)
                    .metodoPago(p.getMetodoPago())
                    .monto(p.getMonto())
                    .folioOperacion(p.getFolioOperacion())
                    .build());
        }
    }

    private BigDecimal efectivoDelDesglose(VentaRequestDto dto) {
        return dto.getPagos().stream()
                .filter(p -> p.getMetodoPago() == METODO_EFECTIVO)
                .map(VentaPagoDetalleRequestDto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<VentaPagoDetalleResponseDto> desgloseDe(Venta venta) {
        if (venta.getMetodoPago() == null || venta.getMetodoPago() != METODO_MIXTO) return List.of();
        return ventaPagoDetalleRepository.findByVenta_IdventaOrderByIdpagoDetalle(venta.getIdventa()).stream()
                .map(p -> VentaPagoDetalleResponseDto.builder()
                        .metodoPago(p.getMetodoPago())
                        .descripcionMetodoPago(descripcionMetodoPago(p.getMetodoPago()))
                        .monto(p.getMonto())
                        .folioOperacion(p.getFolioOperacion())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VentaResponseDto obtenerPorId(Integer idventa) {
        Venta venta = buscarOFallar(idventa);
        return toResponseDto(venta, ventaDetalleRepository.findByVenta_Idventa(idventa));
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDto> listarPorTienda(Integer codti, LocalDateTime desde, LocalDateTime hasta) {
        LocalDateTime desdeEfectivo = desde != null ? desde : LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime hastaEfectivo = hasta != null ? hasta : LocalDateTime.now();
        return ventaRepository.findByTienda_CodtiAndFechaVentaBetweenOrderByFechaVentaDesc(codti, desdeEfectivo, hastaEfectivo)
                .stream()
                .map(v -> toResponseDto(v, ventaDetalleRepository.findByVenta_Idventa(v.getIdventa())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDto> listarPorCliente(Integer idcliente) {
        return ventaRepository.findByCliente_IdclienteOrderByFechaVentaDesc(idcliente)
                .stream()
                .map(v -> toResponseDto(v, ventaDetalleRepository.findByVenta_Idventa(v.getIdventa())))
                .collect(Collectors.toList());
    }

    // ── Cancelar ─────────────────────────────────────────────────────────────

    @Transactional
    public VentaResponseDto cancelar(Integer idventa, Integer idUsuario) {
        Venta venta = buscarOFallar(idventa);

        if (venta.getEstado() != null && venta.getEstado() == ESTADO_CANCELADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta " + idventa + " ya está cancelada.");
        }

        // Una venta que salió de una orden de servicio (con su anticipo) no se cancela por separado.
        if (ordenServicioRepository.existsByVenta_Idventa(idventa)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La venta " + idventa + " salió de una orden de servicio y no se puede cancelar por separado.");
        }

        // Antes de revertir nada: si la venta tiene una cuenta por cobrar con abonos, se bloquea.
        cuentaPorCobrarService.cancelarPorVenta(idventa);

        List<VentaDetalle> detalles = ventaDetalleRepository.findByVenta_Idventa(idventa);

        for (VentaDetalle detalle : detalles) {
            if (detalle.getImei() != null) {
                revertirImei(detalle.getImei());
            } else if (detalle.getCodpro() != null) {
                revertirStockAccesorio(detalle.getCodpro(), detalle.getCantidad());
            }
        }

        venta.setEstado(ESTADO_CANCELADA);
        venta = ventaRepository.save(venta);

        // Revierte la entrada de caja de la venta (si la hubo)
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario no encontrado"));
        movimientoCajaService.registrarCancelacionVenta(venta, usuario);

        return toResponseDto(venta, detalles);
    }

    private void revertirImei(String imei) {
        ProductoImei pi = productoImeiRepository.findByImei(imei)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "El IMEI '" + imei + "' de esta venta ya no existe en el sistema."));

        if (!"VENDIDO".equals(pi.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El IMEI '" + imei + "' ya no está en estado VENDIDO (estado actual: " + pi.getEstado() +
                            "); no se puede revertir automáticamente.");
        }
        pi.setEstado("DISPONIBLE");
        productoImeiRepository.save(pi);

        Producto producto = pi.getProducto();
        BigDecimal stockActual = producto.getStock() != null ? producto.getStock() : BigDecimal.ZERO;
        producto.setStock(stockActual.add(BigDecimal.ONE));
        productoRepository.save(producto);
    }

    private void revertirStockAccesorio(String codpro, Short cantidad) {
        Producto producto = productoRepository.findByCodpro(codpro).orElse(null);
        if (producto == null || producto.getProductoMaster().getTipo() == TipoProducto.SERVICIO) {
            return; // no hay stock que revertir (producto ya no existe, o es un servicio)
        }
        BigDecimal stockActual = producto.getStock() != null ? producto.getStock() : BigDecimal.ZERO;
        producto.setStock(stockActual.add(BigDecimal.valueOf(cantidad)));
        productoRepository.save(producto);
    }

    // ── Resolución de líneas ─────────────────────────────────────────────────

    /** Datos ya validados de una línea de venta, antes de tocar la base de datos. */
    private static class LineaResuelta {
        ProductoMaster productoMaster;
        Producto producto;
        String imei; // solo CELULAR
        Short cantidadSolicitada;
        BigDecimal costoUnitarioCompra;
        BigDecimal precioUnitarioBase;
        BigDecimal precioUnitarioFinal;
        BigDecimal subtotal;
        Boolean esRegalo;
    }

    private LineaResuelta resolverLinea(VentaDetalleRequestDto d, Tienda tienda, Usuario vendedor, boolean desdeOrden) {
        ProductoMaster master = productoMasterRepository.findById(d.getIdprodmaster())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Producto maestro no encontrado: " + d.getIdprodmaster()));

        LineaResuelta linea = new LineaResuelta();
        linea.productoMaster = master;
        linea.cantidadSolicitada = d.getCantidad();
        linea.esRegalo = Boolean.TRUE.equals(d.getEsRegalo());

        if (master.getTipo() == TipoProducto.CELULAR) {
            if (d.getImei() == null || d.getImei().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe indicar el IMEI para vender el artículo CELULAR '" + master.getNombreBase() + "'.");
            }
            if (d.getCantidad() == null || d.getCantidad() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada línea de un CELULAR debe tener cantidad = 1 (un IMEI por línea). IMEI: " + d.getImei());
            }

            ProductoImei pi = productoImeiRepository.findByImei(d.getImei())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "El IMEI '" + d.getImei() + "' no está registrado."));

            Producto producto = pi.getProducto();
            if (!producto.getTienda().getCodti().equals(tienda.getCodti())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El IMEI '" + d.getImei() + "' pertenece a otra tienda.");
            }
            if (!producto.getProductoMaster().getIdprodmaster().equals(master.getIdprodmaster())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El IMEI '" + d.getImei() + "' no corresponde al producto maestro indicado.");
            }
            if (!"DISPONIBLE".equals(pi.getEstado())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El IMEI '" + d.getImei() + "' no está disponible (Estado: " + pi.getEstado() + ").");
            }

            linea.producto = producto;
            linea.imei = d.getImei();
            // El costo de ESTA unidad (p. ej. un usado comprado a un cliente) o, si no tiene, el del modelo.
            linea.costoUnitarioCompra = pi.getCostoUnitario() != null
                    ? pi.getCostoUnitario() : producto.getPreciopro();
            // El precio de referencia es el de ESTA unidad (override si tiene uno propio),
            // no el genérico del modelo — es justo lo que resuelve poder liquidar un
            // equipo específico sin tocar el precio de los demás del mismo modelo.
            linea.precioUnitarioBase = pi.getPrecioVentaOverride() != null
                    ? pi.getPrecioVentaOverride() : producto.getPreciopub();

        } else {
            Producto producto = resolverProductoNoSerial(d, master, tienda);

            if (master.getTipo() != TipoProducto.SERVICIO) {
                BigDecimal cantidad = BigDecimal.valueOf(d.getCantidad());
                BigDecimal stockActual = producto.getStock() != null ? producto.getStock() : BigDecimal.ZERO;
                if (stockActual.subtract(cantidad).compareTo(BigDecimal.ZERO) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Stock insuficiente para '" + master.getNombreBase() + "'. Disponible: " +
                                    stockActual + ", solicitado: " + cantidad);
                }
            }

            linea.producto = producto;
            linea.costoUnitarioCompra = producto.getPreciopro();
            linea.precioUnitarioBase = producto.getPreciopub();
        }

        linea.precioUnitarioFinal = d.getPrecioUnitarioFinal();
        validarRegalo(linea, vendedor, desdeOrden);
        linea.subtotal = linea.precioUnitarioFinal.multiply(BigDecimal.valueOf(linea.cantidadSolicitada));
        return linea;
    }

    /**
     * Un precio de $0 solo es válido en un artículo marcado como regalo, y un regalo debe costar $0.
     * Los vendedores no pueden regalar: lo autoriza un encargado o un administrador.
     */
    private void validarRegalo(LineaResuelta linea, Usuario vendedor, boolean regaloYaAutorizado) {
        boolean precioCero = linea.precioUnitarioFinal.signum() == 0;
        if (linea.esRegalo) {
            if (!precioCero) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un artículo de regalo debe tener precio $0: '" + linea.productoMaster.getNombreBase() + "'.");
            }
            // En la venta de una orden de servicio el regalo ya lo autorizó un encargado al agregarlo a la orden.
            if (!regaloYaAutorizado && vendedor.getRol() == Rol.VENDEDOR) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Solo un encargado o un administrador puede autorizar un regalo.");
            }
        } else if (precioCero) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un precio de $0 solo se permite en artículos marcados como regalo (esRegalo): '"
                            + linea.productoMaster.getNombreBase() + "'.");
        }
    }

    private Producto resolverProductoNoSerial(VentaDetalleRequestDto d, ProductoMaster master, Tienda tienda) {
        if (d.getCodpro() != null && !d.getCodpro().isBlank()) {
            return productoRepository.findByCodproAndTienda_Codti(d.getCodpro(), tienda.getCodti())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "El producto '" + d.getCodpro() + "' no existe en la tienda " + tienda.getCodti()));
        }

        List<Producto> candidatos = productoRepository
                .findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(master.getIdprodmaster(), tienda.getCodti());
        if (candidatos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No hay stock registrado de '" + master.getNombreBase() + "' en la tienda " + tienda.getCodti());
        }
        return candidatos.get(0);
    }

    private void aplicarDescuentoStock(LineaResuelta linea) {
        if (linea.productoMaster.getTipo() == TipoProducto.CELULAR) {
            ProductoImei pi = productoImeiRepository.findByImei(linea.imei)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "El IMEI '" + linea.imei + "' dejó de existir durante el procesamiento de la venta."));
            pi.setEstado("VENDIDO");
            productoImeiRepository.save(pi);

            BigDecimal stockActual = linea.producto.getStock() != null ? linea.producto.getStock() : BigDecimal.ZERO;
            linea.producto.setStock(stockActual.subtract(BigDecimal.ONE));
            productoRepository.save(linea.producto);

        } else if (linea.productoMaster.getTipo() != TipoProducto.SERVICIO) {
            BigDecimal cantidad = BigDecimal.valueOf(linea.cantidadSolicitada);
            BigDecimal stockActual = linea.producto.getStock() != null ? linea.producto.getStock() : BigDecimal.ZERO;
            BigDecimal nuevo = stockActual.subtract(cantidad);
            if (nuevo.compareTo(BigDecimal.ZERO) < 0) {
                // Reconfirmar al momento de aplicar (defensa extra ante concurrencia).
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuficiente para '" + linea.productoMaster.getNombreBase() +
                                "' al momento de confirmar la venta.");
            }
            linea.producto.setStock(nuevo);
            productoRepository.save(linea.producto);
        }
    }

    // ── Mapeo a DTO ──────────────────────────────────────────────────────────

    private VentaResponseDto toResponseDto(Venta venta, List<VentaDetalle> detalles) {
        BigDecimal total = venta.getTotal() != null ? venta.getTotal() : BigDecimal.ZERO;
        BigDecimal abonado = venta.getMontoAbonado() != null ? venta.getMontoAbonado() : BigDecimal.ZERO;
        BigDecimal cambio = abonado.subtract(total);
        if (cambio.compareTo(BigDecimal.ZERO) < 0) cambio = BigDecimal.ZERO;

        return VentaResponseDto.builder()
                .idventa(venta.getIdventa())
                .codti(venta.getTienda().getCodti())
                .nombreTienda(venta.getTienda().getNombre())
                .idCaja(venta.getCaja().getIdCaja())
                .nombreCaja(venta.getCaja().getNombreCaja())
                .idcliente(venta.getCliente() != null ? venta.getCliente().getIdcliente() : null)
                .nombreCliente(venta.getCliente() != null ? venta.getCliente().getNombreCompleto() : null)
                .telefonoCliente(venta.getCliente() != null ? venta.getCliente().getTelefono() : null)
                .usernameVendedor(venta.getUsuarioVendedor().getUsername())
                .nombreVendedor(venta.getUsuarioVendedor().getNombreCompleto())
                .tipoComprobante(venta.getTipoComprobante())
                .descripcionComprobante(descripcionComprobante(venta.getTipoComprobante()))
                .metodoPago(venta.getMetodoPago())
                .descripcionMetodoPago(descripcionMetodoPago(venta.getMetodoPago()))
                .estado(venta.getEstado())
                .descripcionEstado(descripcionEstado(venta.getEstado()))
                .total(total)
                .montoAbonado(abonado)
                .cambio(cambio)
                .observaciones(venta.getObservaciones())
                .fechaVenta(venta.getFechaVenta())
                .detalles(detalles.stream().map(this::toDetalleDto).collect(Collectors.toList()))
                .pagos(desgloseDe(venta))
                .build();
    }

    private VentaDetalleResponseDto toDetalleDto(VentaDetalle d) {
        BigDecimal cantidad = BigDecimal.valueOf(d.getCantidad());
        return VentaDetalleResponseDto.builder()
                .iddetalleVenta(d.getIddetalleVenta())
                .idprodmaster(d.getProductoMaster().getIdprodmaster())
                .nombreProducto(d.getProductoMaster().getNombreBase())
                .codpro(d.getCodpro())
                .cantidad(d.getCantidad())
                .precioUnitarioBase(d.getPrecioUnitarioBase())
                .precioUnitarioFinal(d.getPrecioUnitarioFinal())
                .costoUnitarioCompra(d.getCostoUnitarioCompra())
                .subtotal(d.getPrecioUnitarioFinal().multiply(cantidad))
                .imei(d.getImei())
                .esRegalo(d.getEsRegalo())
                .build();
    }

    private String descripcionComprobante(Byte tipo) {
        if (tipo == null) return "Ticket";
        return switch (tipo) {
            case 2 -> "Factura";
            default -> "Ticket";
        };
    }

    private String descripcionMetodoPago(Byte metodo) {
        if (metodo == null) return "Efectivo";
        return switch (metodo) {
            case 2 -> "Tarjeta";
            case 3 -> "Transferencia";
            case 4 -> "Mixto";
            case 5 -> "PayJoy";
            case 6 -> "Anticipo";
            default -> "Efectivo";
        };
    }

    private String descripcionEstado(Byte estado) {
        if (estado == null) return "Desconocido";
        return switch (estado) {
            case 2 -> "Cancelada";
            default -> "Completada";
        };
    }

    private Venta buscarOFallar(Integer idventa) {
        return ventaRepository.findById(idventa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Venta no encontrada: " + idventa));
    }
}
