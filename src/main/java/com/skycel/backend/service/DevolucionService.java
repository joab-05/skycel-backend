package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.devolucion.*;
import com.skycel.backend.dto.venta.VentaDetalleRequestDto;
import com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Devoluciones de productos vendidos.
 *
 * El personal (vendedor, encargado o administrador) solicita la devolución; un encargado de la tienda o un
 * administrador la aprueba o la rechaza. Al aprobarla se ejecuta todo junto, o nada:
 *  - lo que regresa en buen estado vuelve al inventario (y las unidades a DISPONIBLE); lo dañado se da de baja;
 *  - REEMBOLSO: se devuelve el dinero (el efectivo sale de la caja; tarjeta y transferencia se reembolsan por fuera);
 *  - CAMBIO: el cliente se lleva otro producto de igual o mayor valor. Se genera una venta nueva en la que lo devuelto
 *    cuenta como crédito (método 7) y, si lo nuevo vale más, el cliente paga la diferencia.
 * Una venta admite devoluciones durante los días que fija la configuración del negocio (15 por omisión), si no está cancelada, no es a crédito y no salió de una
 * orden de servicio. Los servicios y los regalos no se devuelven.
 */
@Service
@RequiredArgsConstructor
public class DevolucionService {

    public static final byte TIPO_REEMBOLSO = 1;
    public static final byte TIPO_CAMBIO    = 2;

    public static final byte PENDIENTE = 1;
    public static final byte PROCESADA = 2;
    public static final byte RECHAZADA = 3;

    private static final short FOLIO_DEVOLUCIONES = -3;
    private static final byte VENTA_COMPLETADA = 1;
    private static final byte METODO_CREDITO_DEVOLUCION = 7;
    private static final byte METODO_MIXTO = 4;

    private final DevolucionRepository           devolucionRepository;
    private final DevolucionDetalleRepository    detalleRepository;
    private final DevolucionCambioLineaRepository cambioRepository;
    private final VentaRepository                ventaRepository;
    private final VentaDetalleRepository         ventaDetalleRepository;
    private final ProductoRepository             productoRepository;
    private final ProductoMasterRepository       productoMasterRepository;
    private final ProductoImeiRepository         productoImeiRepository;
    private final CajaRepository                 cajaRepository;
    private final UsuarioRepository              usuarioRepository;
    private final CategoriaFolioRepository       categoriaFolioRepository;
    private final CuentaPorCobrarRepository      cuentaPorCobrarRepository;
    private final OrdenServicioRepository        ordenServicioRepository;
    private final MovimientoCajaService          movimientoCajaService;
    private final MovimientoInventarioService    movimientoInventarioService;
    private final VentaService                   ventaService;
    private final ConfiguracionNegocioService    configuracionNegocioService;

    // ── Consultar qué se puede devolver de una venta ─────────────────────────

    @Transactional(readOnly = true)
    public VentaDevolvibleResponseDto consultarVenta(Integer idventa, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Venta venta = venta(idventa);
        exigirOperaTienda(usuario, venta.getTienda().getCodti());

        String motivo = motivoNoDevolvible(venta);
        List<VentaDevolvibleResponseDto.LineaDto> lineas = new ArrayList<>();
        for (VentaDetalle d : ventaDetalleRepository.findByVenta_Idventa(idventa)) {
            if (!esDevolvible(d)) continue;
            long yaDevuelto = detalleRepository.cantidadEnTramiteOProcesada(d.getIddetalleVenta());
            lineas.add(VentaDevolvibleResponseDto.LineaDto.builder()
                    .iddetalleVenta(d.getIddetalleVenta()).codpro(d.getCodpro())
                    .nombreProducto(d.getProductoMaster().getNombreBase())
                    .tipoProducto(d.getProductoMaster().getTipo().name())
                    .imei(d.getImei()).cantidadVendida(d.getCantidad())
                    .cantidadYaDevuelta(yaDevuelto).disponible(Math.max(0, d.getCantidad() - yaDevuelto))
                    .precioUnitario(d.getPrecioUnitarioFinal()).build());
        }
        if (motivo == null && lineas.isEmpty()) {
            motivo = "Esta venta solo tiene servicios o regalos, que no se devuelven.";
        } else if (motivo == null && lineas.stream().noneMatch(l -> l.getDisponible() > 0)) {
            motivo = "Ya se devolvió (o está en trámite) todo lo que se podía devolver de esta venta.";
        }
        return VentaDevolvibleResponseDto.builder()
                .idventa(venta.getIdventa()).fechaVenta(venta.getFechaVenta().toLocalDate())
                .codti(venta.getTienda().getCodti()).nombreTienda(venta.getTienda().getNombre())
                .nombreCliente(venta.getCliente() != null ? venta.getCliente().getNombreCompleto() : null)
                .descripcionMetodoPago(descripcionMetodo(venta.getMetodoPago()))
                .total(venta.getTotal())
                .elegible(motivo == null).motivo(motivo)
                .devolucionHasta(venta.getFechaVenta().toLocalDate().plusDays(configuracionNegocioService.diasDevolucion()))
                .lineas(lineas).build();
    }

    // ── Solicitar ────────────────────────────────────────────────────────────

    @Transactional
    public DevolucionResponseDto solicitar(DevolucionRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Venta venta = venta(dto.getIdventa());
        exigirOperaTienda(usuario, venta.getTienda().getCodti());

        String noDevolvible = motivoNoDevolvible(venta);
        if (noDevolvible != null) throw new ResponseStatusException(HttpStatus.CONFLICT, noDevolvible);

        // Lo que se devuelve
        Map<Integer, VentaDetalle> renglones = ventaDetalleRepository.findByVenta_Idventa(venta.getIdventa()).stream()
                .collect(Collectors.toMap(VentaDetalle::getIddetalleVenta, d -> d));
        Set<Integer> vistos = new HashSet<>();
        List<DevolucionDetalle> detalles = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (DevolucionRequestDto.LineaDevolucionDto l : dto.getLineas()) {
            VentaDetalle d = renglones.get(l.getIddetalleVenta());
            if (d == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El renglón " + l.getIddetalleVenta() + " no pertenece a la venta " + venta.getIdventa() + ".");
            }
            if (!vistos.add(d.getIddetalleVenta())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El renglón " + d.getIddetalleVenta() + " está repetido.");
            }
            String nombre = d.getProductoMaster().getNombreBase();
            if (!esDevolvible(d)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "«" + nombre + "» no se puede devolver (servicio o regalo).");
            }
            long disponible = d.getCantidad() - detalleRepository.cantidadEnTramiteOProcesada(d.getIddetalleVenta());
            if (l.getCantidad() > disponible) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "De «" + nombre + "» solo se pueden devolver " + Math.max(0, disponible) + " unidad(es).");
            }
            total = total.add(d.getPrecioUnitarioFinal().multiply(BigDecimal.valueOf(l.getCantidad())));
            detalles.add(DevolucionDetalle.builder()
                    .iddetalleVenta(d.getIddetalleVenta()).codpro(d.getCodpro()).nombreProducto(nombre).imei(d.getImei())
                    .cantidad(l.getCantidad()).precioUnitario(d.getPrecioUnitarioFinal())
                    .reingresa(!Boolean.FALSE.equals(l.getReingresa())).build());
        }
        if (total.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lo que se devuelve no tiene valor: no hay nada que reembolsar.");
        }

        // Cómo se resuelve
        Byte metodoReembolso = null, metodoDiferencia = null;
        List<DevolucionCambioLinea> cambio = new ArrayList<>();
        if (dto.getTipo() == TIPO_REEMBOLSO) {
            if (dto.getCambio() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un reembolso no lleva productos de cambio.");
            }
            metodoReembolso = dto.getMetodoReembolso() != null ? dto.getMetodoReembolso() : 1;
            validarMetodo(metodoReembolso, "de reembolso");
        } else {
            if (dto.getCambio() == null || dto.getCambio().getLineas() == null || dto.getCambio().getLineas().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique qué producto se lleva el cliente a cambio.");
            }
            BigDecimal nuevo = BigDecimal.ZERO;
            for (DevolucionRequestDto.LineaCambioDto c : dto.getCambio().getLineas()) {
                ProductoMaster m = productoMasterRepository.findById(c.getIdprodmaster())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto no encontrado: " + c.getIdprodmaster()));
                if (m.getTipo() == TipoProducto.SERVICIO) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un servicio no se puede llevar a cambio.");
                }
                if (m.getTipo() == TipoProducto.CELULAR && (c.getImei() == null || c.getImei().isBlank() || c.getCantidad() != 1)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "«" + m.getNombreBase() + "» es un equipo: indique el IMEI de la unidad (1 por renglón).");
                }
                nuevo = nuevo.add(c.getPrecioUnitarioFinal().multiply(BigDecimal.valueOf(c.getCantidad())));
                cambio.add(DevolucionCambioLinea.builder().idprodmaster(c.getIdprodmaster()).codpro(trimOrNull(c.getCodpro()))
                        .nombreProducto(m.getNombreBase()).imei(trimOrNull(c.getImei())).cantidad(c.getCantidad())
                        .precioUnitario(c.getPrecioUnitarioFinal()).build());
            }
            if (nuevo.compareTo(total) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El producto de cambio ($" + nuevo + ") vale menos que lo devuelto ($" + total
                                + "). Para eso haga un reembolso, o elija algo de igual o mayor valor.");
            }
            if (nuevo.compareTo(total) > 0) {
                metodoDiferencia = dto.getCambio().getMetodoPago() != null ? dto.getCambio().getMetodoPago() : 1;
                validarMetodo(metodoDiferencia, "de la diferencia");
            }
        }

        Caja caja = dto.getIdCaja() != null
                ? cajaRepository.findById(dto.getIdCaja()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caja no encontrada: " + dto.getIdCaja()))
                : venta.getCaja();
        if (caja != null && !caja.getTienda().getCodti().equals(venta.getTienda().getCodti())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caja no pertenece a la tienda de la venta.");
        }

        Devolucion d = devolucionRepository.save(Devolucion.builder()
                .folio(siguienteFolio()).venta(venta).tienda(venta.getTienda()).tipo(dto.getTipo()).estado(PENDIENTE)
                .motivo(dto.getMotivo().trim()).totalDevuelto(total).metodoReembolso(metodoReembolso)
                .metodoDiferencia(metodoDiferencia).folioOperacion(trimOrNull(dto.getFolioOperacion())).caja(caja)
                .usuarioSolicita(usuario).build());
        detalles.forEach(x -> { x.setDevolucion(d); detalleRepository.save(x); });
        cambio.forEach(x -> { x.setDevolucion(d); cambioRepository.save(x); });
        return toDto(d);
    }

    // ── Aprobar / rechazar ───────────────────────────────────────────────────

    /** Ejecuta la devolución: inventario, reembolso o cambio. Si algo falla, no se aplica nada. */
    @Transactional
    public DevolucionResponseDto aprobar(Integer id, ResolverDevolucionDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Devolucion d = pendiente(id, usuario);
        Venta venta = d.getVenta();

        // Puede haber cambiado algo desde que se solicitó (p. ej. la venta se canceló)
        if (venta.getEstado() == null || venta.getEstado() != VENTA_COMPLETADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta " + venta.getIdventa() + " ya no está vigente: rechace la devolución.");
        }

        List<DevolucionDetalle> detalles = detalleRepository.findByDevolucion_IddevolucionOrderByIddetalle(id);
        for (DevolucionDetalle x : detalles) reingresarOBaja(x, d);

        if (d.getTipo() == TIPO_REEMBOLSO) {
            if (d.getMetodoReembolso() == 1) {
                Caja caja = d.getCaja() != null ? d.getCaja() : venta.getCaja();
                if (caja == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la caja de la que sale el efectivo.");
                movimientoCajaService.registrarDevolucionVenta(caja, d, usuario, d.getTotalDevuelto());
            }
        } else {
            VentaResponseDto nueva = ventaService.crearDesdeDevolucion(ventaDelCambio(d), usuario.getIdusuario());
            d.setVentaCambio(ventaRepository.getReferenceById(nueva.getIdventa()));
        }

        d.setEstado(PROCESADA);
        d.setUsuarioResuelve(usuario);
        d.setFechaResolucion(LocalDateTime.now());
        d.setComentarioResolucion(trimOrNull(dto != null ? dto.getComentario() : null));
        return toDto(devolucionRepository.save(d));
    }

    @Transactional
    public DevolucionResponseDto rechazar(Integer id, ResolverDevolucionDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Devolucion d = pendiente(id, usuario);
        String motivo = trimOrNull(dto != null ? dto.getComentario() : null);
        if (motivo == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo del rechazo.");
        d.setEstado(RECHAZADA);
        d.setUsuarioResuelve(usuario);
        d.setFechaResolucion(LocalDateTime.now());
        d.setComentarioResolucion(motivo);
        return toDto(devolucionRepository.save(d));
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DevolucionResponseDto> listar(Integer codti, Byte estado, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Integer filtro = codti;
        if (!esSuperior(usuario)) {
            Integer suya = usuario.getTienda() != null ? usuario.getTienda().getCodti() : null;
            if (suya == null || (codti != null && !codti.equals(suya))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede consultar las devoluciones de su tienda.");
            }
            filtro = suya;
        }
        return devolucionRepository.buscar(filtro, estado).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DevolucionResponseDto obtener(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Devolucion d = devolucion(id);
        exigirOperaTienda(usuario, d.getTienda().getCodti());
        return toDto(d);
    }

    // ── Ejecución ────────────────────────────────────────────────────────────

    /** Lo devuelto en buen estado vuelve al inventario; lo dañado se da de baja. */
    private void reingresarOBaja(DevolucionDetalle x, Devolucion d) {
        Producto producto;
        if (x.getImei() != null) {
            ProductoImei pi = productoImeiRepository.findByImei(x.getImei())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La unidad '" + x.getImei() + "' ya no existe en el sistema."));
            if (!"VENDIDO".equals(pi.getEstado())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La unidad '" + x.getImei() + "' ya no figura como vendida (estado: " + pi.getEstado() + ").");
            }
            pi.setEstado(Boolean.TRUE.equals(x.getReingresa()) ? "DISPONIBLE" : "DEBAJA");
            productoImeiRepository.save(pi);
            producto = pi.getProducto();
        } else {
            producto = productoRepository.findByCodproAndTienda_Codti(x.getCodpro(), d.getTienda().getCodti()).orElse(null);
        }
        if (Boolean.TRUE.equals(x.getReingresa()) && producto != null) {
            BigDecimal antes = producto.getStock() != null ? producto.getStock() : BigDecimal.ZERO;
            producto.setStock(antes.add(BigDecimal.valueOf(x.getCantidad())));
            productoRepository.save(producto);
            movimientoInventarioService.registrar(producto, antes, "DEVOLUCION", "Devolución de venta", d.getFolio());
        }
    }

    /** La venta nueva de un cambio: lo devuelto cuenta como crédito y, si hace falta, se paga la diferencia. */
    private VentaRequestDto ventaDelCambio(Devolucion d) {
        List<DevolucionCambioLinea> lineas = cambioRepository.findByDevolucion_IddevolucionOrderByIdlinea(d.getIddevolucion());
        BigDecimal nuevo = lineas.stream().map(l -> l.getPrecioUnitario().multiply(BigDecimal.valueOf(l.getCantidad()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        Caja caja = d.getCaja() != null ? d.getCaja() : d.getVenta().getCaja();
        if (caja == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la caja para la venta del cambio.");

        VentaRequestDto v = new VentaRequestDto();
        v.setCodti(d.getTienda().getCodti());
        v.setIdCaja(caja.getIdCaja());
        v.setIdcliente(d.getVenta().getCliente() != null ? d.getVenta().getCliente().getIdcliente() : null);
        v.setTipoComprobante((byte) 1);
        v.setMetodoPago(METODO_MIXTO);
        v.setObservaciones("Cambio de la devolución " + d.getFolio() + " (venta #" + d.getVenta().getIdventa() + ")");
        List<VentaPagoDetalleRequestDto> pagos = new ArrayList<>();
        pagos.add(pago(METODO_CREDITO_DEVOLUCION, d.getTotalDevuelto(), d.getFolio()));
        BigDecimal diferencia = nuevo.subtract(d.getTotalDevuelto());
        if (diferencia.signum() > 0) pagos.add(pago(d.getMetodoDiferencia(), diferencia, d.getFolioOperacion()));
        v.setPagos(pagos);
        v.setDetalles(lineas.stream().map(l -> {
            VentaDetalleRequestDto x = new VentaDetalleRequestDto();
            x.setIdprodmaster(l.getIdprodmaster());
            x.setCodpro(l.getCodpro());
            x.setCantidad(l.getCantidad());
            x.setPrecioUnitarioFinal(l.getPrecioUnitario());
            x.setImei(l.getImei());
            x.setEsRegalo(false);
            return x;
        }).collect(Collectors.toList()));
        return v;
    }

    private VentaPagoDetalleRequestDto pago(byte metodo, BigDecimal monto, String folio) {
        VentaPagoDetalleRequestDto p = new VentaPagoDetalleRequestDto();
        p.setMetodoPago(metodo);
        p.setMonto(monto);
        p.setFolioOperacion(folio);
        return p;
    }

    // ── Reglas ───────────────────────────────────────────────────────────────

    /** null si la venta admite devoluciones; si no, la razón. */
    private String motivoNoDevolvible(Venta venta) {
        if (venta.getEstado() == null || venta.getEstado() != VENTA_COMPLETADA) return "La venta está cancelada.";
        if (cuentaPorCobrarRepository.findByVenta_Idventa(venta.getIdventa()).isPresent()) {
            return "La venta es a crédito: se maneja desde Cuentas por cobrar.";
        }
        if (ordenServicioRepository.existsByVenta_Idventa(venta.getIdventa())) {
            return "La venta salió de una orden de servicio: use la garantía del trabajo.";
        }
        int dias = configuracionNegocioService.diasDevolucion();
        LocalDate limite = venta.getFechaVenta().toLocalDate().plusDays(dias);
        if (LocalDate.now().isAfter(limite)) {
            return "Ya pasó el plazo de " + dias + " días para devoluciones (venció el " + limite + ").";
        }
        return null;
    }

    private boolean esDevolvible(VentaDetalle d) {
        return d.getProductoMaster().getTipo() != TipoProducto.SERVICIO
                && !Boolean.TRUE.equals(d.getEsRegalo())
                && d.getPrecioUnitarioFinal() != null && d.getPrecioUnitarioFinal().signum() > 0;
    }

    private void validarMetodo(Byte metodo, String de) {
        if (metodo == null || metodo < 1 || metodo > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El método " + de + " debe ser 1 Efectivo, 2 Tarjeta o 3 Transferencia.");
        }
    }

    private Devolucion pendiente(Integer id, Usuario usuario) {
        Devolucion d = devolucion(id);
        if (!esSuperior(usuario) && !(usuario.getRol() == Rol.ENCARGADO_TIENDA && operaTienda(usuario, d.getTienda().getCodti()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un encargado de la tienda o un administrador resuelve devoluciones.");
        }
        if (d.getEstado() != PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La devolución " + d.getFolio() + " ya fue resuelta.");
        }
        return d;
    }

    private String siguienteFolio() {
        categoriaFolioRepository.incrementar(FOLIO_DEVOLUCIONES);
        return "DEV-" + String.format("%06d", categoriaFolioRepository.obtenerUltimoFolioGenerado(FOLIO_DEVOLUCIONES));
    }

    private Venta venta(Integer id) {
        return ventaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada: " + id));
    }

    private Devolucion devolucion(Integer id) {
        return devolucionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Devolución no encontrada: " + id));
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private boolean esSuperior(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    private boolean operaTienda(Usuario u, Integer codti) {
        return u.getTienda() != null && u.getTienda().getCodti().equals(codti);
    }

    private void exigirOperaTienda(Usuario u, Integer codti) {
        boolean ok = esSuperior(u) || ((u.getRol() == Rol.ENCARGADO_TIENDA || u.getRol() == Rol.VENDEDOR) && operaTienda(u, codti));
        if (!ok) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para operar devoluciones de la tienda " + codti + ".");
    }

    private String trimOrNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String descripcionMetodo(Byte m) {
        if (m == null) return null;
        return switch (m) {
            case 1 -> "Efectivo";
            case 2 -> "Tarjeta";
            case 3 -> "Transferencia";
            case 4 -> "Mixto";
            case 5 -> "PayJoy";
            default -> "Otro";
        };
    }

    // ── Mapeo ────────────────────────────────────────────────────────────────

    private DevolucionResponseDto toDto(Devolucion d) {
        List<DevolucionResponseDto.LineaDto> lineas = detalleRepository.findByDevolucion_IddevolucionOrderByIddetalle(d.getIddevolucion()).stream()
                .map(x -> DevolucionResponseDto.LineaDto.builder()
                        .iddetalleVenta(x.getIddetalleVenta()).codpro(x.getCodpro()).nombreProducto(x.getNombreProducto()).imei(x.getImei())
                        .cantidad(x.getCantidad()).precioUnitario(x.getPrecioUnitario())
                        .subtotal(x.getPrecioUnitario().multiply(BigDecimal.valueOf(x.getCantidad()))).reingresa(x.getReingresa()).build())
                .collect(Collectors.toList());
        List<DevolucionResponseDto.CambioLineaDto> cambio = cambioRepository.findByDevolucion_IddevolucionOrderByIdlinea(d.getIddevolucion()).stream()
                .map(x -> DevolucionResponseDto.CambioLineaDto.builder()
                        .codpro(x.getCodpro()).nombreProducto(x.getNombreProducto()).imei(x.getImei()).cantidad(x.getCantidad())
                        .precioUnitario(x.getPrecioUnitario()).subtotal(x.getPrecioUnitario().multiply(BigDecimal.valueOf(x.getCantidad()))).build())
                .collect(Collectors.toList());
        BigDecimal totalCambio = cambio.isEmpty() ? null : cambio.stream().map(DevolucionResponseDto.CambioLineaDto::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return DevolucionResponseDto.builder()
                .iddevolucion(d.getIddevolucion()).folio(d.getFolio()).idventa(d.getVenta().getIdventa())
                .codti(d.getTienda().getCodti()).nombreTienda(d.getTienda().getNombre())
                .nombreCliente(d.getVenta().getCliente() != null ? d.getVenta().getCliente().getNombreCompleto() : null)
                .tipo(d.getTipo()).tipoDisplay(d.getTipo() == TIPO_REEMBOLSO ? "Reembolso" : "Cambio")
                .estado(d.getEstado()).estadoDisplay(switch (d.getEstado()) { case PENDIENTE -> "Pendiente"; case PROCESADA -> "Procesada"; default -> "Rechazada"; })
                .motivo(d.getMotivo()).totalDevuelto(d.getTotalDevuelto())
                .metodoReembolso(d.getMetodoReembolso()).descripcionMetodoReembolso(descripcionMetodo(d.getMetodoReembolso()))
                .totalCambio(totalCambio).diferencia(totalCambio != null ? totalCambio.subtract(d.getTotalDevuelto()) : null)
                .metodoDiferencia(d.getMetodoDiferencia())
                .idventaCambio(d.getVentaCambio() != null ? d.getVentaCambio().getIdventa() : null)
                .nombreSolicita(d.getUsuarioSolicita().getNombreCompleto())
                .nombreResuelve(d.getUsuarioResuelve() != null ? d.getUsuarioResuelve().getNombreCompleto() : null)
                .comentarioResolucion(d.getComentarioResolucion())
                .fechaSolicitud(d.getFechaSolicitud()).fechaResolucion(d.getFechaResolucion())
                .lineas(lineas).cambio(cambio).build();
    }
}
