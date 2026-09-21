package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.dto.cpc.CuentaPorCobrarRequestDto;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CuentaPorCobrarService {

    private final CuentaPorCobrarRepository cpcRepository;
    private final PagoCuentaRepository      pagoRepository;
    private final ClienteRepository         clienteRepository;
    private final UsuarioRepository         usuarioRepository;
    private final VentaRepository           ventaRepository;
    private final MovimientoCajaService     movimientoCajaService;

    private static final byte METODO_EFECTIVO = 1;

    /** Plazo por defecto de una venta a crédito cuando no se indica fecha de vencimiento. */
    public static final int DIAS_CREDITO_DEFAULT = 30;

    // ── Listar ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarTodas() {
        return cpcRepository.findAll().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarActivas() {
        // Todas menos las completamente pagadas
        return cpcRepository.findByEstadoNot((byte) 2).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalPendiente", cpcRepository.totalPendiente());
        r.put("totalVencido",   cpcRepository.totalVencido());
        r.put("cuentasActivas", cpcRepository.findByEstadoNot((byte) 2).size());
        return r;
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> crear(CuentaPorCobrarRequestDto dto) {
        if (cpcRepository.existsByNoFactura(dto.getNoFactura()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cuenta con el folio: " + dto.getNoFactura());

        Cliente cliente = clienteRepository.findById(dto.getIdcliente())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente no encontrado"));

        Venta venta = null;
        if (dto.getIdventa() != null) {
            venta = ventaRepository.findById(dto.getIdventa())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Venta no encontrada: " + dto.getIdventa()));
            if (venta.getCliente() != null && !venta.getCliente().getIdcliente().equals(cliente.getIdcliente())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La venta " + venta.getIdventa() + " pertenece a otro cliente.");
            }
            if (cpcRepository.findByVenta_Idventa(venta.getIdventa()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La venta " + venta.getIdventa() + " ya tiene una cuenta por cobrar.");
            }
        }

        CuentaPorCobrar cpc = CuentaPorCobrar.builder()
                .noFactura(dto.getNoFactura())
                .cliente(cliente)
                .venta(venta)
                .fechaEmision(dto.getFechaEmision() != null ? dto.getFechaEmision() : LocalDate.now())
                .fechaVencimiento(dto.getFechaVencimiento())
                .montoTotal(dto.getMontoTotal())
                .montoPagado(BigDecimal.ZERO)
                .estado((byte) 0)
                .observaciones(dto.getObservaciones())
                .build();
        actualizarEstado(cpc); // una cuenta creada con fecha de vencimiento pasada nace como Vencida

        return toMap(cpcRepository.save(cpc));
    }

    /**
     * Cuenta por el saldo de una venta a crédito (monto abonado menor al total). Se llama desde
     * VentaService dentro de la misma transacción de la venta. El abono inicial ya quedó cobrado
     * en la venta (y en caja, si fue en efectivo), por eso la cuenta nace solo con el saldo.
     */
    @Transactional
    public CuentaPorCobrar crearDesdeVenta(Venta venta, BigDecimal saldo, LocalDate fechaVencimiento) {
        String noFactura = String.format("V-%06d", venta.getIdventa());
        if (cpcRepository.existsByNoFactura(noFactura)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con el folio: " + noFactura);
        }
        LocalDate hoy = venta.getFechaVenta() != null ? venta.getFechaVenta().toLocalDate() : LocalDate.now();
        LocalDate vencimiento = fechaVencimiento != null ? fechaVencimiento : hoy.plusDays(DIAS_CREDITO_DEFAULT);

        CuentaPorCobrar cpc = CuentaPorCobrar.builder()
                .noFactura(noFactura)
                .cliente(venta.getCliente())
                .venta(venta)
                .fechaEmision(hoy)
                .fechaVencimiento(vencimiento)
                .montoTotal(saldo)
                .montoPagado(BigDecimal.ZERO)
                .estado((byte) 0)
                .observaciones("Saldo de la venta #" + venta.getIdventa() + " (total $" + venta.getTotal()
                        + ", abono inicial $" + venta.getMontoAbonado() + ")")
                .build();
        return cpcRepository.save(cpc);
    }

    /**
     * Al cancelar una venta a crédito su cuenta deja de tener sentido. Si aún no tiene abonos se
     * elimina; si ya hay dinero cobrado se bloquea la cancelación, para no perder ese registro.
     */
    @Transactional
    public void cancelarPorVenta(Integer idventa) {
        cpcRepository.findByVenta_Idventa(idventa).ifPresent(cuenta -> {
            if (cuenta.getMontoPagado().signum() > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La venta " + idventa + " tiene abonos por $" + cuenta.getMontoPagado()
                                + " en la cuenta por cobrar " + cuenta.getNoFactura()
                                + ". Resuelva esos abonos antes de cancelarla.");
            }
            cpcRepository.delete(cuenta);
        });
    }

    // ── Registrar pago ────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> registrarPago(Integer idcuenta, BigDecimal monto,
                                             Byte metodoPago, String notas,
                                             Integer idusuario, Integer idCaja) {
        CuentaPorCobrar cpc = buscarOFallar(idcuenta);

        if (monto.compareTo(BigDecimal.ZERO) <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a 0.");

        BigDecimal saldo = cpc.getMontoTotal().subtract(cpc.getMontoPagado());
        if (monto.compareTo(saldo) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El pago ($" + monto + ") supera el saldo pendiente ($" + saldo + ").");

        Usuario usuario = usuarioRepository.findById(idusuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario no encontrado"));

        // Registrar abono
        PagoCuenta pago = PagoCuenta.builder()
                .cuentaPorCobrar(cpc)
                .usuario(usuario)
                .monto(monto)
                .metodoPago(metodoPago != null ? metodoPago : 1)
                .notas(notas)
                .build();
        pago = pagoRepository.save(pago);

        // Solo el efectivo entra a la caja (tarjeta/transferencia/PayJoy no pasan por el cajón)
        if (pago.getMetodoPago() != null && pago.getMetodoPago() == METODO_EFECTIVO) {
            movimientoCajaService.registrarAbonoCuenta(idCaja, cpc, pago, usuario);
        }

        // Actualizar monto pagado y estado
        cpc.setMontoPagado(cpc.getMontoPagado().add(monto));
        actualizarEstado(cpc);
        return toMap(cpcRepository.save(cpc));
    }

    // ── Job automático: marcar vencidas ───────────────────────────────────────

    @Scheduled(cron = "0 0 1 * * *") // todos los días a la 1am
    @Transactional
    public void marcarVencidas() {
        List<CuentaPorCobrar> vencidas = cpcRepository
                .findByFechaVencimientoBeforeAndEstadoIn(
                        LocalDate.now(), List.of((byte) 0, (byte) 1));
        vencidas.forEach(c -> { c.setEstado((byte) 3); cpcRepository.save(c); });
    }

    // ── Historial de pagos ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historialPagos(Integer idcuenta) {
        buscarOFallar(idcuenta);
        return pagoRepository.findByCuentaPorCobrarIdcuentaOrderByFechaPagoDesc(idcuenta)
                .stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("idpago",      p.getIdpago());
                    m.put("monto",       p.getMonto());
                    m.put("metodoPago",  metodoPagoDisplay(p.getMetodoPago()));
                    m.put("fechaPago",   p.getFechaPago());
                    m.put("notas",       p.getNotas());
                    m.put("usuario",     p.getUsuario().getNombreCompleto());
                    return m;
                }).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void actualizarEstado(CuentaPorCobrar cpc) {
        BigDecimal saldo = cpc.getMontoTotal().subtract(cpc.getMontoPagado());
        if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
            cpc.setEstado((byte) 2); // Pagado
        } else if (cpc.getMontoPagado().compareTo(BigDecimal.ZERO) > 0) {
            cpc.setEstado((byte) 1); // PagadoParcial
        } else if (LocalDate.now().isAfter(cpc.getFechaVencimiento())) {
            cpc.setEstado((byte) 3); // Vencido
        }
    }

    private Map<String, Object> toMap(CuentaPorCobrar c) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), c.getFechaVencimiento());
        BigDecimal saldo = c.getMontoTotal().subtract(c.getMontoPagado());
        byte estado = c.getEstado() != null ? c.getEstado() : 0;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idcuenta",         c.getIdcuenta());
        m.put("noFactura",        c.getNoFactura());
        m.put("idcliente",        c.getCliente().getIdcliente());
        m.put("nombreCliente",    c.getCliente().getNombreCompleto());
        m.put("idventa",          c.getVenta() != null ? c.getVenta().getIdventa() : null);
        m.put("fechaEmision",     c.getFechaEmision());
        m.put("fechaVencimiento", c.getFechaVencimiento());
        m.put("diasVencimiento",  dias);          // negativo = vencida, positivo = días restantes
        m.put("montoTotal",       c.getMontoTotal());
        m.put("montoPagado",      c.getMontoPagado());
        m.put("saldoPendiente",   saldo);
        m.put("estado",           estado);
        m.put("estadoDisplay",    estadoDisplay(estado));
        m.put("estadoColor",      estadoColor(estado));
        m.put("observaciones",    c.getObservaciones());
        m.put("fechaRegistro",    c.getFechaRegistro());
        return m;
    }

    private String estadoDisplay(byte e) {
        return switch (e) {
            case 1  -> "Pagado Parcial";
            case 2  -> "Pagado";
            case 3  -> "Vencido";
            default -> "Pendiente";
        };
    }

    private String estadoColor(byte e) {
        return switch (e) {
            case 1  -> "#f59e0b";  // amarillo
            case 2  -> "#16a34a";  // verde
            case 3  -> "#dc2626";  // rojo
            default -> "#3b82f6";  // azul
        };
    }

    private String metodoPagoDisplay(Byte m) {
        if (m == null) return "Efectivo";
        return switch (m) {
            case 2  -> "Tarjeta";
            case 3  -> "Transferencia";
            case 4  -> "PayJoy";
            default -> "Efectivo";
        };
    }

    private CuentaPorCobrar buscarOFallar(Integer id) {
        return cpcRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cuenta por cobrar no encontrada: " + id));
    }
}