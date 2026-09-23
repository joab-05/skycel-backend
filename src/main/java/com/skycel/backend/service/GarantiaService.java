package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.garantia.*;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Garantías de productos vendidos, con viaje al proveedor.
 *
 * Camino normal: 1 Recibido en sucursal → 2 En tránsito a bodega → 3 Recibido en bodega → 4 En tránsito al proveedor →
 * 5 En el proveedor → 6 Reparado / 7 Cambio físico / 8 Rechazado → 9 Viaje de retorno → 10 Listo para entrega → 11 Entregado.
 * Un rechazo puede decidirse en la sucursal (1→8) o en bodega (3→8); si el equipo nunca salió de la sucursal se pasa
 * directo a Listo para entrega, y si salió, primero regresa (9).
 *
 * Solo procede si la venta sigue vigente, el producto aparece en ella y no ha vencido su garantía (los días de garantía
 * del producto contados desde la venta). La fecha límite de solución es el ingreso + 30 días hábiles.
 */
@Service
@RequiredArgsConstructor
public class GarantiaService {

    public static final byte RECIBIDO_SUCURSAL    = 1;
    public static final byte EN_TRANSITO_BODEGA   = 2;
    public static final byte RECIBIDO_BODEGA      = 3;
    public static final byte TRANSITO_A_PROVEEDOR = 4;
    public static final byte EN_PROVEEDOR         = 5;
    public static final byte REPARADO             = 6;
    public static final byte CAMBIO_FISICO        = 7;
    public static final byte RECHAZADO            = 8;
    public static final byte VIAJE_RETORNO        = 9;
    public static final byte LISTO_ENTREGA        = 10;
    public static final byte ENTREGADO            = 11;

    /** Plazo para resolver una garantía, en días hábiles (lunes a viernes). */
    public static final int DIAS_HABILES_SOLUCION = 30;

    /** Contador reservado para los folios (categoria_folio no tiene FK real). */
    private static final short FOLIO_GARANTIAS = -2;

    private static final Map<Byte, Set<Byte>> TRANSICIONES = Map.of(
            RECIBIDO_SUCURSAL,    Set.of(EN_TRANSITO_BODEGA, RECHAZADO),
            EN_TRANSITO_BODEGA,   Set.of(RECIBIDO_BODEGA),
            RECIBIDO_BODEGA,      Set.of(TRANSITO_A_PROVEEDOR, RECHAZADO),
            TRANSITO_A_PROVEEDOR, Set.of(EN_PROVEEDOR),
            EN_PROVEEDOR,         Set.of(REPARADO, CAMBIO_FISICO, RECHAZADO),
            REPARADO,             Set.of(VIAJE_RETORNO),
            CAMBIO_FISICO,        Set.of(VIAJE_RETORNO),
            RECHAZADO,            Set.of(VIAJE_RETORNO, LISTO_ENTREGA),
            VIAJE_RETORNO,        Set.of(LISTO_ENTREGA),
            LISTO_ENTREGA,        Set.of(ENTREGADO));

    /** Pasos que puede registrar el personal de la sucursal; el resto (bodega y proveedor) los registra un administrador. */
    private static final Set<String> PASOS_DE_SUCURSAL = Set.of("1>2", "1>8", "8>10", "9>10", "10>11");

    private final GarantiaRepository          garantiaRepository;
    private final GarantiaHistorialRepository historialRepository;
    private final VentaRepository             ventaRepository;
    private final VentaDetalleRepository      ventaDetalleRepository;
    private final ProductoRepository          productoRepository;
    private final ProductoImeiRepository      productoImeiRepository;
    private final TiendaRepository            tiendaRepository;
    private final UsuarioRepository           usuarioRepository;
    private final CategoriaFolioRepository    categoriaFolioRepository;
    private final NotificacionService         notificacionService;

    /** Lo que se necesita saber de un producto vendido para reclamarle la garantía. */
    private record Cobertura(Venta venta, Producto producto, ProductoMaster master, String imei, int dias, LocalDate fechaVenta, LocalDate vence) {}

    // ── Recibir un reclamo ───────────────────────────────────────────────────

    @Transactional
    public GarantiaResponseDto crear(GarantiaRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Tienda tienda = tiendaQueOpera(dto.getCodti(), usuario);

        Cobertura c = evaluar(dto.getIdventa(), dto.getImei(), dto.getCodpro());

        // Un mismo equipo no puede tener dos reclamos abiertos a la vez
        boolean yaAbierta = garantiaRepository.findByVenta_IdventaAndProducto_Idproducto(c.venta().getIdventa(), c.producto().getIdproducto())
                .stream().anyMatch(g -> g.getEstadoActual() != ENTREGADO && Objects.equals(g.getImei(), c.imei()));
        if (yaAbierta) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya hay una garantía abierta de '" + c.master().getNombreBase() + "' para la venta " + c.venta().getIdventa() + ".");
        }

        Cliente cliente = c.venta().getCliente();
        String nombre = primero(dto.getNombreContacto(), cliente != null ? cliente.getNombreCompleto() : null);
        String telefono = primero(dto.getTelefonoContacto(), cliente != null ? cliente.getTelefono() : null);
        if (nombre == null || telefono == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indique el nombre y el teléfono de contacto (nombreContacto y telefonoContacto): la venta no tiene cliente con esos datos.");
        }
        if (digitos(telefono).length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono de contacto debe tener al menos 4 dígitos.");
        }

        LocalDate hoy = LocalDate.now();
        Garantia g = garantiaRepository.save(Garantia.builder()
                .venta(c.venta()).producto(c.producto()).usuarioRecibe(usuario).tienda(tienda)
                .folioSeguimiento(siguienteFolio())
                .imei(c.imei())
                .fallaReportada(dto.getFallaReportada().trim())
                .diagnosticoInicial(trimOrNull(dto.getDiagnosticoInicial()))
                .nombreContacto(nombre).telefonoContacto(telefono)
                .proveedor(c.producto().getProveedor())
                .estadoActual(RECIBIDO_SUCURSAL)
                .fechaLimiteSolucion(sumarDiasHabiles(hoy, DIAS_HABILES_SOLUCION))
                .build());
        registrar(g, usuario, "Producto recibido en " + tienda.getNombre()
                + (g.getDiagnosticoInicial() != null ? ". Estado: " + g.getDiagnosticoInicial() : ""));
        return toDto(g);
    }

    /** ¿Este producto de esta venta todavía tiene garantía? Sirve para decidirlo antes de recibir el equipo. */
    @Transactional(readOnly = true)
    public ElegibilidadGarantiaResponseDto elegibilidad(Integer idventa, String imei, String codpro, String username) {
        usuarioPorUsername(username);
        try {
            Cobertura c = evaluar(idventa, imei, codpro);
            return ElegibilidadGarantiaResponseDto.builder()
                    .elegible(true).nombreProducto(c.master().getNombreBase()).diasGarantia(c.dias())
                    .fechaVenta(c.fechaVenta()).garantiaVigenteHasta(c.vence())
                    .diasRestantes(ChronoUnit.DAYS.between(LocalDate.now(), c.vence()))
                    .build();
        } catch (ResponseStatusException e) {
            return ElegibilidadGarantiaResponseDto.builder().elegible(false).motivo(e.getReason()).build();
        }
    }

    // ── Seguimiento ──────────────────────────────────────────────────────────

    @Transactional
    public GarantiaResponseDto avanzar(Integer id, AvanceGarantiaRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Garantia g = garantia(id);
        exigirOperaTienda(usuario, g.getTienda().getCodti());

        byte desde = g.getEstadoActual();
        byte hacia = dto.getEstado();
        Set<Byte> siguientes = TRANSICIONES.getOrDefault(desde, Set.of());
        if (!siguientes.contains(hacia)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, siguientes.isEmpty()
                    ? "La garantía " + g.getFolioSeguimiento() + " ya fue entregada."
                    : "No se puede pasar de «" + estadoDisplay(desde) + "» a «" + estadoDisplay(hacia) + "». Desde ahí se puede ir a: "
                            + siguientes.stream().sorted().map(e -> e + " " + estadoDisplay(e)).collect(Collectors.joining(", ")) + ".");
        }

        boolean salioDeSucursal = historialRepository.findByGarantia_IdgarantiaOrderByIdhistorial(id).stream()
                .anyMatch(h -> h.getEstado() == EN_TRANSITO_BODEGA);
        if (desde == RECHAZADO && hacia == LISTO_ENTREGA && salioDeSucursal) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta garantía ya salió de la sucursal: primero debe registrarse el viaje de retorno (9).");
        }
        if (desde == RECHAZADO && hacia == VIAJE_RETORNO && !salioDeSucursal) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta garantía nunca salió de la sucursal: pásela directo a «Listo para entrega» (10).");
        }

        if (!esSuperior(usuario) && !PASOS_DE_SUCURSAL.contains(desde + ">" + hacia)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El paso a «" + estadoDisplay(hacia) + "» lo registra bodega o un administrador.");
        }
        if ((hacia == REPARADO || hacia == CAMBIO_FISICO || hacia == RECHAZADO) && trimOrNull(dto.getComentario()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El comentario es obligatorio al resolver la garantía: indique qué se hizo o por qué se rechazó.");
        }
        String reemplazo = trimOrNull(dto.getImeiReemplazo());
        if (reemplazo != null) {
            if (hacia != CAMBIO_FISICO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El IMEI de reemplazo solo aplica en un cambio físico (7).");
            }
            if (!reemplazo.matches("[A-Za-z0-9]{5,20}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El IMEI o serie de reemplazo debe tener entre 5 y 20 caracteres alfanuméricos.");
            }
            g.setImeiReemplazo(reemplazo);
        }

        g.setEstadoActual(hacia);
        registrar(g, usuario, trimOrNull(dto.getComentario()) != null ? dto.getComentario().trim()
                : estadoDisplay(hacia) + (reemplazo != null ? " (equipo nuevo: " + reemplazo + ")" : ""));
        GarantiaResponseDto resultado = toDto(garantiaRepository.save(g));
        notificacionService.notificarTienda(g.getTienda().getCodti(), "GARANTIA_AVANCE",
                "Garantía " + g.getFolioSeguimiento() + ": ahora está «" + estadoDisplay(hacia) + "»",
                "#/garantia/" + g.getIdgarantia());
        return resultado;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GarantiaResponseDto obtener(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Garantia g = garantia(id);
        exigirOperaTienda(usuario, g.getTienda().getCodti());
        return toDto(g);
    }

    @Transactional(readOnly = true)
    public GarantiaResponseDto obtenerPorFolio(String folio, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Garantia g = garantiaRepository.findByFolioSeguimiento(folio.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Garantía no encontrada: " + folio));
        exigirOperaTienda(usuario, g.getTienda().getCodti());
        return toDto(g);
    }

    /**
     * Garantías, con filtros opcionales. Un administrador ve las de todas las sucursales (o la que indique); el resto
     * solo las que se recibieron en su sucursal.
     */
    @Transactional(readOnly = true)
    public List<GarantiaResponseDto> listar(Integer codti, Byte estado, boolean soloAbiertas, boolean vencidas, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Integer sucursal = codti;
        if (!esSuperior(usuario)) {
            sucursal = codti != null ? codti : (usuario.getTienda() != null ? usuario.getTienda().getCodti() : null);
            if (sucursal == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la sucursal (codti).");
            exigirOperaTienda(usuario, sucursal);
        }
        return garantiaRepository.buscar(sucursal, estado, soloAbiertas, vencidas, LocalDate.now())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Consulta del cliente, sin iniciar sesión: folio + últimos 4 dígitos del teléfono de contacto. Cualquier falla
     * (folio que no existe o teléfono que no coincide) da la misma respuesta, para que nadie pueda ir descubriendo folios.
     */
    @Transactional(readOnly = true)
    public GarantiaPublicaResponseDto consultaPublica(String folio, String telefono) {
        String ultimos = ultimosCuatro(telefono);
        Garantia g = garantiaRepository.findByFolioSeguimiento(folio == null ? "" : folio.trim().toUpperCase())
                .filter(x -> ultimos != null && ultimos.equals(ultimosCuatro(x.getTelefonoContacto())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos una garantía con ese folio y teléfono."));

        String imei = g.getImei() == null ? null
                : (g.getImei().length() > 4 ? "•".repeat(g.getImei().length() - 4) + g.getImei().substring(g.getImei().length() - 4) : g.getImei());
        return GarantiaPublicaResponseDto.builder()
                .folio(g.getFolioSeguimiento())
                .producto(g.getProducto().getProductoMaster().getNombreBase())
                .imei(imei)
                .sucursal(g.getTienda().getNombre())
                .fechaIngreso(g.getFechaIngreso())
                .fechaLimiteSolucion(g.getFechaLimiteSolucion())
                .estado(estadoDisplay(g.getEstadoActual()))
                .mensaje(mensajeParaElCliente(g.getEstadoActual()))
                .avance(historialRepository.findByGarantia_IdgarantiaOrderByIdhistorial(g.getIdgarantia()).stream()
                        .map(h -> GarantiaPublicaResponseDto.PasoDto.builder().estado(estadoDisplay(h.getEstado())).fecha(h.getFechaMovimiento()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    // ── Piezas internas ──────────────────────────────────────────────────────

    /** Valida que el producto de la venta pueda entrar a garantía, y calcula hasta cuándo cubre. */
    private Cobertura evaluar(Integer idventa, String imei, String codpro) {
        Venta venta = ventaRepository.findById(idventa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada: " + idventa));
        if (venta.getEstado() == null || venta.getEstado() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta " + idventa + " está cancelada: no tiene garantía.");
        }

        List<VentaDetalle> lineas = ventaDetalleRepository.findByVenta_Idventa(idventa);
        String imeiBuscado = trimOrNull(imei);
        String codigoBuscado = trimOrNull(codpro);
        VentaDetalle linea;
        if (imeiBuscado != null) {
            linea = lineas.stream().filter(l -> imeiBuscado.equals(l.getImei())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El IMEI '" + imeiBuscado + "' no aparece en la venta " + idventa + "."));
        } else if (codigoBuscado != null) {
            linea = lineas.stream().filter(l -> codigoBuscado.equals(l.getCodpro())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto '" + codigoBuscado + "' no aparece en la venta " + idventa + "."));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el imei (equipos) o el codpro (accesorios).");
        }

        ProductoMaster master = linea.getProductoMaster();
        if (master.getTipo() == TipoProducto.SERVICIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un servicio no entra a garantía con proveedor.");
        }
        Integer dias = master.getDiasGarantia();
        if (dias == null || dias <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "'" + master.getNombreBase() + "' no tiene garantía definida.");
        }
        LocalDate fechaVenta = venta.getFechaVenta().toLocalDate();
        LocalDate vence = fechaVenta.plusDays(dias);
        if (LocalDate.now().isAfter(vence)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La garantía de '" + master.getNombreBase() + "' venció el " + vence
                    + " (" + dias + " días desde la venta del " + fechaVenta + ").");
        }

        Producto producto;
        if (imeiBuscado != null) {
            producto = productoImeiRepository.findByImei(imeiBuscado).map(ProductoImei::getProducto)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El IMEI '" + imeiBuscado + "' no está registrado en el inventario."));
        } else {
            producto = productoRepository.findByCodproAndTienda_Codti(codigoBuscado, venta.getTienda().getCodti())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto '" + codigoBuscado + "' no existe en la tienda de la venta."));
        }
        return new Cobertura(venta, producto, master, imeiBuscado, dias, fechaVenta, vence);
    }

    /** Suma días hábiles (lunes a viernes) a una fecha. No considera días festivos. */
    public static LocalDate sumarDiasHabiles(LocalDate desde, int dias) {
        LocalDate fecha = desde;
        int sumados = 0;
        while (sumados < dias) {
            fecha = fecha.plusDays(1);
            if (fecha.getDayOfWeek() != DayOfWeek.SATURDAY && fecha.getDayOfWeek() != DayOfWeek.SUNDAY) sumados++;
        }
        return fecha;
    }

    private void registrar(Garantia g, Usuario usuario, String comentario) {
        historialRepository.save(GarantiaHistorial.builder().garantia(g).usuario(usuario).estado(g.getEstadoActual()).comentario(comentario).build());
    }

    private String siguienteFolio() {
        categoriaFolioRepository.incrementar(FOLIO_GARANTIAS);
        return "GAR-" + String.format("%06d", categoriaFolioRepository.obtenerUltimoFolioGenerado(FOLIO_GARANTIAS));
    }

    private Garantia garantia(Integer id) {
        return garantiaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Garantía no encontrada: " + id));
    }

    private String primero(String a, String b) {
        String x = trimOrNull(a);
        return x != null ? x : trimOrNull(b);
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** Los últimos 4 dígitos de un teléfono, o null si tiene menos de 4. */
    private String ultimosCuatro(String telefono) {
        String d = digitos(telefono);
        return d.length() < 4 ? null : d.substring(d.length() - 4);
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    // ── Permisos: ROOT/ADMIN cualquier sucursal; encargado y vendedor, la suya ─

    private boolean esSuperior(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    private boolean puedeOperarTienda(Usuario u, Integer codti) {
        return esSuperior(u) || ((u.getRol() == Rol.ENCARGADO_TIENDA || u.getRol() == Rol.VENDEDOR)
                && u.getTienda() != null && u.getTienda().getCodti().equals(codti));
    }

    private void exigirOperaTienda(Usuario u, Integer codti) {
        if (!puedeOperarTienda(u, codti)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para operar las garantías de la sucursal " + codti + ".");
        }
    }

    private Tienda tiendaQueOpera(Integer codtiSolicitado, Usuario usuario) {
        Integer codti = codtiSolicitado != null ? codtiSolicitado : (usuario.getTienda() != null ? usuario.getTienda().getCodti() : null);
        if (codti == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la sucursal que recibe (codti).");
        exigirOperaTienda(usuario, codti);
        return tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no encontrada: " + codti));
    }

    // ── Mapeo ────────────────────────────────────────────────────────────────

    private GarantiaResponseDto toDto(Garantia g) {
        ProductoMaster master = g.getProducto().getProductoMaster();
        LocalDate fechaVenta = g.getVenta().getFechaVenta() != null ? g.getVenta().getFechaVenta().toLocalDate() : null;
        LocalDate vigenteHasta = fechaVenta != null && master.getDiasGarantia() != null ? fechaVenta.plusDays(master.getDiasGarantia()) : null;
        boolean abierta = g.getEstadoActual() != ENTREGADO;
        Long restantes = abierta && g.getFechaLimiteSolucion() != null ? ChronoUnit.DAYS.between(LocalDate.now(), g.getFechaLimiteSolucion()) : null;

        return GarantiaResponseDto.builder()
                .idgarantia(g.getIdgarantia()).folio(g.getFolioSeguimiento()).idventa(g.getVenta().getIdventa())
                .codpro(g.getProducto().getCodpro()).nombreProducto(master.getNombreBase()).tipoProducto(master.getTipo().name())
                .imei(g.getImei()).fechaVentaOriginal(fechaVenta).garantiaVigenteHasta(vigenteHasta)
                .codti(g.getTienda().getCodti()).nombreTienda(g.getTienda().getNombre())
                .idusuarioRecibe(g.getUsuarioRecibe().getIdusuario()).nombreRecibe(g.getUsuarioRecibe().getNombreCompleto())
                .nombreContacto(g.getNombreContacto()).telefonoContacto(g.getTelefonoContacto())
                .fallaReportada(g.getFallaReportada()).diagnosticoInicial(g.getDiagnosticoInicial())
                .proveedor(g.getProveedor() != null ? (g.getProveedor().getNombreCorto() != null ? g.getProveedor().getNombreCorto() : g.getProveedor().getNombreFiscal()) : null)
                .estado(g.getEstadoActual()).estadoDisplay(estadoDisplay(g.getEstadoActual()))
                .fechaIngreso(g.getFechaIngreso()).fechaLimiteSolucion(g.getFechaLimiteSolucion())
                .diasRestantes(restantes).vencida(abierta && restantes != null && restantes < 0)
                .imeiReemplazo(g.getImeiReemplazo())
                .historial(historialRepository.findByGarantia_IdgarantiaOrderByIdhistorial(g.getIdgarantia()).stream()
                        .map(h -> GarantiaResponseDto.HistorialDto.builder()
                                .estado(h.getEstado()).estadoDisplay(estadoDisplay(h.getEstado())).comentario(h.getComentario())
                                .nombreUsuario(h.getUsuario() != null ? h.getUsuario().getNombreCompleto() : null)
                                .fecha(h.getFechaMovimiento()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    private String estadoDisplay(Byte estado) {
        return switch (estado) {
            case RECIBIDO_SUCURSAL    -> "Recibido en sucursal";
            case EN_TRANSITO_BODEGA   -> "En tránsito a bodega";
            case RECIBIDO_BODEGA      -> "Recibido en bodega";
            case TRANSITO_A_PROVEEDOR -> "En tránsito al proveedor";
            case EN_PROVEEDOR         -> "En el proveedor";
            case REPARADO             -> "Reparado por el proveedor";
            case CAMBIO_FISICO        -> "Cambio físico";
            case RECHAZADO            -> "Rechazado";
            case VIAJE_RETORNO        -> "Viaje de retorno";
            case LISTO_ENTREGA        -> "Listo para entrega";
            case ENTREGADO            -> "Entregado";
            default                   -> "Desconocido";
        };
    }

    private String mensajeParaElCliente(Byte estado) {
        return switch (estado) {
            case RECIBIDO_SUCURSAL    -> "Recibimos tu producto en la sucursal y lo estamos revisando.";
            case EN_TRANSITO_BODEGA   -> "Tu producto va en camino a nuestra bodega.";
            case RECIBIDO_BODEGA      -> "Tu producto llegó a nuestra bodega.";
            case TRANSITO_A_PROVEEDOR -> "Tu producto va en camino con el proveedor.";
            case EN_PROVEEDOR         -> "El proveedor está revisando tu producto.";
            case REPARADO             -> "El proveedor reparó tu producto.";
            case CAMBIO_FISICO        -> "El proveedor aprobó el cambio de tu producto por uno nuevo.";
            case RECHAZADO            -> "Tu garantía no procedió. Pasa a la sucursal para que te expliquen el motivo.";
            case VIAJE_RETORNO        -> "Tu producto va de regreso a la sucursal.";
            case LISTO_ENTREGA        -> "Tu producto está listo. Pasa a recogerlo a la sucursal.";
            case ENTREGADO            -> "Tu producto ya fue entregado.";
            default                   -> "";
        };
    }
}
