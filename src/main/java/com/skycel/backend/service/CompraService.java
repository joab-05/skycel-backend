package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ProductoUpdateDTO;
import com.skycel.backend.domain.dto.request.StockAjusteDTO;
import com.skycel.backend.domain.dto.request.UnidadEquipoDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.compra.CompraLineaRequestDto;
import com.skycel.backend.dto.compra.CompraRequestDto;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compras de mercancía a proveedor y su cuenta por pagar (el espejo de {@link CuentaPorCobrarService}, pero del
 * lado del dinero que la tienda debe, no del que le deben). Registrar una compra suma el stock recibido — mismo
 * camino que un ajuste de tipo ENTRADA en Inventario — y, si el producto es un accesorio o servicio con costo,
 * actualiza su precio de compra: la compra es la fuente natural de ese dato.
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository      compraRepository;
    private final PagoCompraRepository  pagoRepository;
    private final ProveedorRepository   proveedorRepository;
    private final TiendaRepository      tiendaRepository;
    private final ProductoRepository    productoRepository;
    private final UsuarioRepository     usuarioRepository;
    private final ProductoService       productoService;
    private final MovimientoCajaService movimientoCajaService;

    private static final byte METODO_EFECTIVO = 1;

    // ── Listar ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Integer codti, boolean soloActivas, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Integer filtro = codti;
        if (!esAdmin(usuario)) {
            if (usuario.getTienda() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tu usuario no tiene una sucursal fija.");
            }
            if (filtro != null && !filtro.equals(usuario.getTienda().getCodti())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes ver las compras de tu tienda.");
            }
            filtro = usuario.getTienda().getCodti();
        }
        List<Compra> lista = filtro != null
                ? (soloActivas ? compraRepository.findByTienda_CodtiAndEstadoNotOrderByFechaDesc(filtro, (byte) 2)
                                : compraRepository.findByTienda_CodtiOrderByFechaDesc(filtro))
                : (soloActivas ? compraRepository.findByEstadoNotOrderByFechaDesc((byte) 2)
                                : compraRepository.findAllByOrderByFechaDesc());
        return lista.stream().map(this::toMap).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalPendiente", compraRepository.totalPendiente());
        r.put("totalVencido",   compraRepository.totalVencido());
        r.put("comprasActivas", compraRepository.findByEstadoNotOrderByFechaDesc((byte) 2).size());
        return r;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(Integer idcompra) {
        return toMap(buscarOFallar(idcompra));
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> crear(CompraRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);

        if (dto.getIdProveedor() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el proveedor.");
        if (dto.getCodti() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la sucursal que recibe.");
        if (dto.getLineas() == null || dto.getLineas().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La compra necesita al menos un producto.");

        if (!esAdmin(usuario) && (usuario.getTienda() == null || !usuario.getTienda().getCodti().equals(dto.getCodti()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes registrar compras para tu propia tienda.");
        }

        Proveedor proveedor = proveedorRepository.findById(dto.getIdProveedor())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado."));
        if (!Boolean.TRUE.equals(proveedor.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El proveedor '" + proveedor.getNombreFiscal() + "' está desactivado.");
        }
        Tienda tienda = tiendaRepository.findById(dto.getCodti())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada."));

        LocalDate fecha = dto.getFecha() != null ? dto.getFecha() : LocalDate.now();
        int diasCredito = proveedor.getDiasCredito() != null ? proveedor.getDiasCredito() : 0;
        LocalDate vencimiento = dto.getFechaVencimiento() != null ? dto.getFechaVencimiento() : fecha.plusDays(diasCredito);

        Compra compra = compraRepository.save(Compra.builder()
                .proveedor(proveedor).tienda(tienda).usuario(usuario)
                .folioProveedor(dto.getFolioProveedor())
                .fecha(fecha).fechaVencimiento(vencimiento)
                .montoTotal(BigDecimal.ZERO).montoPagado(BigDecimal.ZERO)
                .estado((byte) 0)
                .observaciones(dto.getObservaciones())
                .build());

        BigDecimal total = BigDecimal.ZERO;
        List<CompraDetalle> lineas = new ArrayList<>();
        for (CompraLineaRequestDto l : dto.getLineas()) {
            if (l.getCodpro() == null || l.getCodpro().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el código de un producto en la compra.");
            Producto producto = productoRepository.findByCodproAndTienda_Codti(l.getCodpro(), dto.getCodti())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El producto '" + l.getCodpro() + "' no existe en esa sucursal."));
            if (producto.getProductoMaster().getTipo() == TipoProducto.SERVICIO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + l.getCodpro() + "' es un servicio: no maneja inventario, no se compra.");
            }
            boolean esEquipo = producto.getProductoMaster().getTipo() == TipoProducto.CELULAR
                    || producto.getProductoMaster().getTipo() == TipoProducto.TABLET;

            BigDecimal cantidad;
            BigDecimal costoTotalLinea;
            StockAjusteDTO ajuste = new StockAjusteDTO();
            ajuste.setTipo("ENTRADA");
            ajuste.setComentario("Compra #" + compra.getIdcompra() + " a " + nombreProveedor(proveedor)
                    + (dto.getFolioProveedor() != null && !dto.getFolioProveedor().isBlank() ? " (folio " + dto.getFolioProveedor() + ")" : ""));

            if (esEquipo) {
                List<UnidadEquipoDTO> unidades = l.getUnidades();
                if (unidades == null || unidades.isEmpty())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "'" + l.getCodpro() + "' se controla por IMEI: indica las unidades recibidas.");
                costoTotalLinea = BigDecimal.ZERO;
                for (UnidadEquipoDTO u : unidades) {
                    if (u.getCostoUnitario() == null || u.getCostoUnitario().signum() <= 0)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Indica el costo del IMEI '" + u.getImei() + "'.");
                    if (u.getCondicion() == null || u.getCondicion().isBlank()) u.setCondicion("NUEVO");
                    costoTotalLinea = costoTotalLinea.add(u.getCostoUnitario());
                }
                cantidad = BigDecimal.valueOf(unidades.size());
                ajuste.setCantidad(cantidad);
                ajuste.setUnidades(unidades);
            } else {
                cantidad = l.getCantidad();
                if (cantidad == null || cantidad.signum() <= 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la cantidad recibida de '" + l.getCodpro() + "'.");
                if (l.getCostoUnitario() == null || l.getCostoUnitario().signum() <= 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el costo de '" + l.getCodpro() + "'.");
                costoTotalLinea = cantidad.multiply(l.getCostoUnitario());
                ajuste.setCantidad(cantidad);
            }

            productoService.ajustarStock(l.getCodpro(), dto.getCodti(), ajuste);
            if (!esEquipo) {
                ProductoUpdateDTO actualiza = new ProductoUpdateDTO();
                actualiza.setPrecioCompra(l.getCostoUnitario());
                productoService.actualizar(l.getCodpro(), dto.getCodti(), actualiza);
            }

            BigDecimal costoUnitarioPromedio = costoTotalLinea.divide(cantidad, 2, java.math.RoundingMode.HALF_UP);
            lineas.add(CompraDetalle.builder()
                    .compra(compra).producto(producto)
                    .cantidad(cantidad).costoUnitario(costoUnitarioPromedio).subtotal(costoTotalLinea)
                    .build());
            total = total.add(costoTotalLinea);
        }

        compra.setLineas(lineas);
        compra.setMontoTotal(total);
        actualizarEstado(compra);
        return toMap(compraRepository.save(compra));
    }

    // ── Registrar pago ────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> registrarPago(Integer idcompra, BigDecimal monto, Byte metodoPago,
                                              String notas, String username, Integer idCaja) {
        Compra compra = buscarOFallar(idcompra);
        Usuario usuario = usuarioPorUsername(username);
        if (!esAdmin(usuario) && (usuario.getTienda() == null || !usuario.getTienda().getCodti().equals(compra.getTienda().getCodti()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes pagar las compras de tu tienda.");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a 0.");

        BigDecimal saldo = compra.getMontoTotal().subtract(compra.getMontoPagado());
        if (monto.compareTo(saldo) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El pago ($" + monto + ") supera el saldo pendiente ($" + saldo + ").");

        PagoCompra pago = pagoRepository.save(PagoCompra.builder()
                .compra(compra).usuario(usuario).monto(monto)
                .metodoPago(metodoPago != null ? metodoPago : METODO_EFECTIVO)
                .notas(notas)
                .build());

        if (pago.getMetodoPago() != null && pago.getMetodoPago() == METODO_EFECTIVO) {
            movimientoCajaService.registrarPagoCompra(idCaja, compra, pago, usuario);
        }

        compra.setMontoPagado(compra.getMontoPagado().add(monto));
        actualizarEstado(compra);
        return toMap(compraRepository.save(compra));
    }

    // ── Job automático: marcar vencidas ───────────────────────────────────────

    @Scheduled(cron = "0 15 1 * * *") // todos los días a la 1:15am
    @Transactional
    public void marcarVencidas() {
        List<Compra> vencidas = compraRepository.findByFechaVencimientoBeforeAndEstadoIn(LocalDate.now(), List.of((byte) 0, (byte) 1));
        vencidas.forEach(c -> { c.setEstado((byte) 3); compraRepository.save(c); });
    }

    // ── Historial de pagos ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historialPagos(Integer idcompra) {
        buscarOFallar(idcompra);
        return pagoRepository.findByCompra_IdcompraOrderByFechaPagoDesc(idcompra).stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("idpago",     p.getIdpago());
                    m.put("monto",      p.getMonto());
                    m.put("metodoPago", metodoPagoDisplay(p.getMetodoPago()));
                    m.put("fechaPago",  p.getFechaPago());
                    m.put("notas",      p.getNotas());
                    m.put("usuario",    p.getUsuario().getNombreCompleto());
                    return m;
                }).collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void actualizarEstado(Compra c) {
        BigDecimal saldo = c.getMontoTotal().subtract(c.getMontoPagado());
        if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
            c.setEstado((byte) 2); // Pagada
        } else if (c.getMontoPagado().compareTo(BigDecimal.ZERO) > 0) {
            c.setEstado((byte) 1); // PagadaParcial
        } else if (LocalDate.now().isAfter(c.getFechaVencimiento())) {
            c.setEstado((byte) 3); // Vencida
        }
    }

    private boolean esAdmin(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    private String nombreProveedor(Proveedor p) {
        return p.getNombreCorto() != null ? p.getNombreCorto() : p.getNombreFiscal();
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private Compra buscarOFallar(Integer id) {
        return compraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada: " + id));
    }

    private Map<String, Object> toMap(Compra c) {
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), c.getFechaVencimiento());
        BigDecimal saldo = c.getMontoTotal().subtract(c.getMontoPagado());
        byte estado = c.getEstado() != null ? c.getEstado() : 0;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idcompra",         c.getIdcompra());
        m.put("idProveedor",      c.getProveedor().getIdproveedor());
        m.put("nombreProveedor",  nombreProveedor(c.getProveedor()));
        m.put("codti",            c.getTienda().getCodti());
        m.put("nombreTienda",     c.getTienda().getNombre());
        m.put("folioProveedor",   c.getFolioProveedor());
        m.put("fecha",            c.getFecha());
        m.put("fechaVencimiento", c.getFechaVencimiento());
        m.put("diasVencimiento",  dias);
        m.put("montoTotal",       c.getMontoTotal());
        m.put("montoPagado",      c.getMontoPagado());
        m.put("saldoPendiente",   saldo);
        m.put("estado",           estado);
        m.put("estadoDisplay",    estadoDisplay(estado));
        m.put("estadoColor",      estadoColor(estado));
        m.put("observaciones",    c.getObservaciones());
        m.put("registradoPor",    c.getUsuario() != null ? c.getUsuario().getNombreCompleto() : null);
        m.put("fechaRegistro",    c.getFechaRegistro());
        if (c.getLineas() != null) {
            m.put("lineas", c.getLineas().stream().map(l -> {
                Map<String, Object> lm = new LinkedHashMap<>();
                lm.put("codpro",        l.getProducto().getCodpro());
                lm.put("nombreProducto", l.getProducto().getProductoMaster().getNombreBase());
                lm.put("cantidad",      l.getCantidad());
                lm.put("costoUnitario", l.getCostoUnitario());
                lm.put("subtotal",      l.getSubtotal());
                return lm;
            }).collect(Collectors.toList()));
        }
        return m;
    }

    private String estadoDisplay(byte e) {
        return switch (e) {
            case 1  -> "Pagada Parcial";
            case 2  -> "Pagada";
            case 3  -> "Vencida";
            default -> "Pendiente";
        };
    }

    private String estadoColor(byte e) {
        return switch (e) {
            case 1  -> "#f59e0b";
            case 2  -> "#16a34a";
            case 3  -> "#dc2626";
            default -> "#3b82f6";
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
}
