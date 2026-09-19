package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.traspaso.*;
import com.skycel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Traspasos de mercancía entre tiendas.
 *
 *  - ENVIO: la tienda origen manda mercancía. Al despachar el stock sale del origen y las unidades de equipo
 *    quedan TRASPASADO (en tránsito). La tienda destino confirma la recepción física: el stock entra a su
 *    producto (que se crea si no lo tenía) y las unidades pasan a DISPONIBLE en la destino.
 *  - SOLICITUD: una tienda le pide mercancía a otra. La que surte la lee, y la acepta (elige los IMEI de los
 *    equipos y se genera el envío, ligado por idtraspasoRef) o la rechaza con un motivo.
 *  - Un envío se puede anular mientras no se reciba (el stock y las unidades vuelven al origen); una solicitud,
 *    mientras no se resuelva.
 */
@Service
@RequiredArgsConstructor
public class TraspasoService {

    public static final byte TIPO_ENVIO     = 1;
    public static final byte TIPO_SOLICITUD = 2;

    public static final byte ENVIADO   = 1;
    public static final byte RECIBIDO  = 2;
    public static final byte LEIDO     = 3;
    public static final byte ACEPTADA  = 4;
    public static final byte RECHAZADA = 5;

    private static final String IMEI_EN_TRANSITO = "TRASPASADO";
    private static final String IMEI_DISPONIBLE  = "DISPONIBLE";

    private final TraspasoRepository        traspasoRepository;
    private final TraspasoDetalleRepository detalleRepository;
    private final TiendaRepository          tiendaRepository;
    private final UsuarioRepository         usuarioRepository;
    private final ProductoRepository        productoRepository;
    private final ProductoImeiRepository    productoImeiRepository;
    private final ProductoService           productoService;

    /** Renglón ya resuelto para despachar: producto de la tienda origen, cantidad y, en equipos, sus IMEI. */
    private record LineaDespacho(String codpro, BigDecimal cantidad, List<String> imeis) {}

    // ── Crear ────────────────────────────────────────────────────────────────

    @Transactional
    public TraspasoResponseDto crearEnvio(EnvioRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Tienda origen = tiendaQueOpera(dto.getCodtiOrigen(), usuario, "origen");
        Tienda destino = tienda(dto.getCodtiDestino());
        validarDistintas(origen, destino);

        List<LineaDespacho> lineas = dto.getLineas().stream()
                .map(l -> new LineaDespacho(l.getCodpro().trim(), l.getCantidad(), l.getImeis()))
                .collect(Collectors.toList());
        return toDto(despachar(origen, destino, usuario, lineas, null));
    }

    @Transactional
    public TraspasoResponseDto crearSolicitud(SolicitudRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Tienda solicitante = tiendaQueOpera(dto.getCodtiOrigen(), usuario, "que pide");
        Tienda surte = tienda(dto.getCodtiDestino());
        validarDistintas(solicitante, surte);

        Traspaso solicitud = traspasoRepository.save(Traspaso.builder()
                .tipo(TIPO_SOLICITUD).tiendaOrigen(solicitante).tiendaDestino(surte)
                .usuarioCrea(usuario).estado(ENVIADO).activo(true).build());

        Set<String> vistos = new HashSet<>();
        for (TraspasoLineaRequestDto l : dto.getLineas()) {
            String codpro = l.getCodpro().trim();
            if (!vistos.add(codpro)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto '" + codpro + "' está repetido en la solicitud.");
            }
            if (l.getImeis() != null && !l.getImeis().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "En una solicitud no se indican los IMEI: los elige la tienda que surte.");
            }
            Producto p = productoDe(codpro, surte.getCodti(), "la tienda que surte");
            if (p.getProductoMaster().getTipo() == TipoProducto.SERVICIO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un servicio no se puede traspasar: '" + codpro + "'.");
            }
            if (p.getProductoMaster().getTipo() == TipoProducto.CELULAR) enteroPositivo(l.getCantidad(), codpro);
            detalleRepository.save(TraspasoDetalle.builder().traspaso(solicitud).codpro(codpro).cantidad(l.getCantidad()).build());
        }
        return toDto(solicitud);
    }

    // ── Envío: recibir / anular ──────────────────────────────────────────────

    /** La tienda destino confirma la recepción física: el stock y las unidades pasan a su inventario. */
    @Transactional
    public TraspasoResponseDto recibir(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Traspaso t = obtenerActivo(id);
        if (t.getTipo() != TIPO_ENVIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se recibe un envío; una solicitud se acepta o se rechaza.");
        }
        validarOpera(usuario, t.getTiendaDestino().getCodti());
        if (t.getEstado() != ENVIADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El envío " + id + " ya fue recibido.");
        }

        Map<Integer, Producto> destinoPorOrigen = new LinkedHashMap<>();
        for (TraspasoDetalle d : detalleRepository.findByTraspaso_IdtraspasoOrderByIddetalle(id)) {
            Producto enOrigen = productoDe(d.getCodpro(), t.getTiendaOrigen().getCodti(), "la tienda origen");
            Producto enDestino = destinoPorOrigen.computeIfAbsent(enOrigen.getIdproducto(),
                    k -> productoEnDestino(enOrigen, t.getTiendaDestino()));
            if (d.getImei() != null) {
                ProductoImei pi = productoImeiRepository.findByImei(d.getImei())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "La unidad '" + d.getImei() + "' del envío ya no existe."));
                pi.setProducto(enDestino);
                pi.setEstado(IMEI_DISPONIBLE);
                productoImeiRepository.save(pi);
            }
            enDestino.setStock(stockDe(enDestino).add(d.getCantidad()));
        }
        destinoPorOrigen.values().forEach(productoRepository::save);

        t.setEstado(RECIBIDO);
        t.setUsuarioValida(usuario);
        return toDto(traspasoRepository.save(t));
    }

    /**
     * Anula un envío que aún no se recibió (el stock y las unidades regresan al origen) o una solicitud
     * que aún no se resuelve. Lo hace la tienda que lo creó.
     */
    @Transactional
    public TraspasoResponseDto anular(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Traspaso t = traspasoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traspaso no encontrado: " + id));
        validarOpera(usuario, t.getTiendaOrigen().getCodti());
        if (!Boolean.TRUE.equals(t.getActivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El traspaso " + id + " ya está anulado.");
        }

        if (t.getTipo() == TIPO_ENVIO) {
            if (t.getEstado() != ENVIADO) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Un envío ya recibido no se puede anular.");
            }
            for (TraspasoDetalle d : detalleRepository.findByTraspaso_IdtraspasoOrderByIddetalle(id)) {
                Producto p = productoDe(d.getCodpro(), t.getTiendaOrigen().getCodti(), "la tienda origen");
                if (d.getImei() != null) {
                    productoImeiRepository.findByImei(d.getImei()).ifPresent(pi -> {
                        if (IMEI_EN_TRANSITO.equals(pi.getEstado())) {
                            pi.setEstado(IMEI_DISPONIBLE);
                            productoImeiRepository.save(pi);
                        }
                    });
                }
                p.setStock(stockDe(p).add(d.getCantidad()));
                productoRepository.save(p);
            }
        } else if (t.getEstado() != ENVIADO && t.getEstado() != LEIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Una solicitud ya resuelta no se puede anular.");
        }

        t.setActivo(false);
        return toDto(traspasoRepository.save(t));
    }

    // ── Solicitud: leer / aceptar / rechazar ─────────────────────────────────

    /** La tienda que surte marca la solicitud como leída. */
    @Transactional
    public TraspasoResponseDto leer(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Traspaso s = solicitudPendiente(id, usuario);
        if (s.getEstado() == ENVIADO) {
            s.setEstado(LEIDO);
            s.setUsuarioValida(usuario);
            s = traspasoRepository.save(s);
        }
        return toDto(s);
    }

    /** Acepta la solicitud: elige las unidades de los equipos, despacha el envío ligado y la cierra como Aceptada. */
    @Transactional
    public TraspasoResponseDto aceptar(Integer id, AceptarSolicitudRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Traspaso s = solicitudPendiente(id, usuario);

        List<TraspasoDetalle> pedidos = detalleRepository.findByTraspaso_IdtraspasoOrderByIddetalle(id);
        Map<String, List<String>> elegidas = new HashMap<>();
        if (dto != null && dto.getEquipos() != null) {
            for (AceptarSolicitudRequestDto.UnidadesElegidasDto e : dto.getEquipos()) {
                elegidas.put(e.getCodpro() == null ? "" : e.getCodpro().trim(), e.getImeis() == null ? List.of() : e.getImeis());
            }
        }
        Set<String> codigosPedidos = pedidos.stream().map(TraspasoDetalle::getCodpro).collect(Collectors.toSet());
        for (String codpro : elegidas.keySet()) {
            if (!codigosPedidos.contains(codpro)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto '" + codpro + "' no está en la solicitud.");
            }
        }

        List<LineaDespacho> lineas = pedidos.stream()
                .map(d -> new LineaDespacho(d.getCodpro(), d.getCantidad(), elegidas.getOrDefault(d.getCodpro(), List.of())))
                .collect(Collectors.toList());
        // quien surte (destino de la solicitud) es el origen del envío, y quien pidió (origen) lo recibe
        Traspaso envio = despachar(s.getTiendaDestino(), s.getTiendaOrigen(), usuario, lineas, s.getIdtraspaso());

        s.setEstado(ACEPTADA);
        s.setUsuarioValida(usuario);
        traspasoRepository.save(s);
        return toDto(envio);
    }

    @Transactional
    public TraspasoResponseDto rechazar(Integer id, RechazoRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Traspaso s = solicitudPendiente(id, usuario);
        s.setEstado(RECHAZADA);
        s.setUsuarioValida(usuario);
        s.setMotivoRechazo(dto.getMotivo().trim());
        return toDto(traspasoRepository.save(s));
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TraspasoResponseDto obtener(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Traspaso t = traspasoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traspaso no encontrado: " + id));
        if (!puedeOperar(usuario, t.getTiendaOrigen().getCodti()) && !puedeOperar(usuario, t.getTiendaDestino().getCodti())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a los traspasos de otras tiendas.");
        }
        return toDto(t);
    }

    /** Traspasos que entran o salen de una tienda. */
    @Transactional(readOnly = true)
    public List<TraspasoResponseDto> listar(Integer codti, Byte tipo, Byte estado, boolean incluirAnulados, String username) {
        validarOpera(usuarioPorUsername(username), codti);
        return traspasoRepository.buscar(codti, tipo, estado, incluirAnulados).stream().map(this::toDto).collect(Collectors.toList());
    }

    /** Lo que la tienda tiene por atender: envíos por recibir y solicitudes por leer o resolver. */
    @Transactional(readOnly = true)
    public List<TraspasoResponseDto> pendientes(Integer codti, String username) {
        validarOpera(usuarioPorUsername(username), codti);
        return traspasoRepository.pendientesDeLaTienda(codti).stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Despacho (compartido por el envío directo y por aceptar una solicitud) ─

    private Traspaso despachar(Tienda origen, Tienda destino, Usuario usuario, List<LineaDespacho> lineas, Integer idRef) {
        Traspaso envio = traspasoRepository.save(Traspaso.builder()
                .tipo(TIPO_ENVIO).tiendaOrigen(origen).tiendaDestino(destino)
                .usuarioCrea(usuario).estado(ENVIADO).activo(true).idtraspasoRef(idRef).build());

        Set<String> imeisEnElEnvio = new HashSet<>();
        for (LineaDespacho l : lineas) {
            Producto p = productoDe(l.codpro(), origen.getCodti(), "la tienda origen");
            TipoProducto tipo = p.getProductoMaster().getTipo();
            BigDecimal stock = stockDe(p);
            List<String> imeis = l.imeis() == null ? List.of() : l.imeis();

            if (tipo == TipoProducto.SERVICIO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un servicio no se puede traspasar: '" + l.codpro() + "'.");
            }
            if (tipo == TipoProducto.CELULAR) {
                int n = enteroPositivo(l.cantidad(), l.codpro());
                if (imeis.size() != n) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Debe indicar exactamente " + n + " IMEI para '" + l.codpro() + "' (uno por unidad).");
                }
                for (String imei : imeis) {
                    if (!imeisEnElEnvio.add(imei)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El IMEI '" + imei + "' está repetido en el envío.");
                    }
                    ProductoImei pi = productoImeiRepository.findByImei(imei)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El IMEI '" + imei + "' no está registrado."));
                    if (!pi.getProducto().getIdproducto().equals(p.getIdproducto())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El IMEI '" + imei + "' no pertenece a '" + l.codpro() + "' en la tienda origen.");
                    }
                    if (!IMEI_DISPONIBLE.equals(pi.getEstado())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El IMEI '" + imei + "' no está disponible (estado: " + pi.getEstado() + ").");
                    }
                    pi.setEstado(IMEI_EN_TRANSITO);
                    productoImeiRepository.save(pi);
                    detalleRepository.save(TraspasoDetalle.builder().traspaso(envio).codpro(l.codpro()).cantidad(BigDecimal.ONE).imei(imei).build());
                }
            } else {
                if (!imeis.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "'" + l.codpro() + "' es un accesorio: no lleva IMEI.");
                }
                detalleRepository.save(TraspasoDetalle.builder().traspaso(envio).codpro(l.codpro()).cantidad(l.cantidad()).build());
            }

            BigDecimal cantidad = tipo == TipoProducto.CELULAR ? BigDecimal.valueOf(imeis.size()) : l.cantidad();
            if (stock.compareTo(cantidad) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Stock insuficiente de '" + l.codpro() + "' en la tienda origen. Disponible: " + stock + ", solicitado: " + cantidad);
            }
            p.setStock(stock.subtract(cantidad));
            productoRepository.save(p);
        }
        return envio;
    }

    /** El producto equivalente en la tienda destino (mismo maestro y color); si no lo tiene, se crea con los datos del origen. */
    private Producto productoEnDestino(Producto enOrigen, Tienda destino) {
        return productoRepository
                .findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(enOrigen.getProductoMaster().getIdprodmaster(), destino.getCodti())
                .stream()
                .filter(p -> Objects.equals(idColorDe(enOrigen), idColorDe(p)))
                .findFirst()
                .orElseGet(() -> productoRepository.save(Producto.builder()
                        .codpro(productoService.generarCodigoPara(enOrigen.getProductoMaster()))
                        .tienda(destino)
                        .productoMaster(enOrigen.getProductoMaster())
                        .magnitud(enOrigen.getMagnitud())
                        .color(enOrigen.getColor())
                        .proveedor(enOrigen.getProveedor())
                        .stock(BigDecimal.ZERO)
                        .stockMinimo(BigDecimal.ZERO)
                        .preciopro(enOrigen.getPreciopro())
                        .preciopub(enOrigen.getPreciopub())
                        .activo(true).publico(true).rezagado(false)
                        .build()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Short idColorDe(Producto p) {
        return p.getColor() != null ? p.getColor().getIdcolor() : null;
    }

    private Traspaso obtenerActivo(Integer id) {
        Traspaso t = traspasoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Traspaso no encontrado: " + id));
        if (!Boolean.TRUE.equals(t.getActivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El traspaso " + id + " está anulado.");
        }
        return t;
    }

    /** Una solicitud activa, por leer o resolver, que la tienda que surte (el usuario) puede atender. */
    private Traspaso solicitudPendiente(Integer id, Usuario usuario) {
        Traspaso s = obtenerActivo(id);
        if (s.getTipo() != TIPO_SOLICITUD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El traspaso " + id + " es un envío, no una solicitud.");
        }
        validarOpera(usuario, s.getTiendaDestino().getCodti());
        if (s.getEstado() != ENVIADO && s.getEstado() != LEIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud " + id + " ya fue resuelta.");
        }
        return s;
    }

    private Producto productoDe(String codpro, Integer codti, String donde) {
        return productoRepository.findByCodproAndTienda_Codti(codpro, codti)
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El producto '" + codpro + "' no existe en " + donde + "."));
    }

    private BigDecimal stockDe(Producto p) {
        return p.getStock() != null ? p.getStock() : BigDecimal.ZERO;
    }

    /** Cantidad entera positiva (los equipos se cuentan por unidad). */
    private int enteroPositivo(BigDecimal cantidad, String codpro) {
        try {
            int n = cantidad.intValueExact();
            if (n > 0) return n;
        } catch (ArithmeticException ignorada) {
            // no es entera: cae al error de abajo
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad de '" + codpro + "' debe ser un número entero mayor a 0.");
    }

    private Tienda tienda(Integer codti) {
        return tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no encontrada: " + codti));
    }

    /** La tienda con la que actúa el usuario: la indicada o, si se omite, la suya. Debe poder operarla. */
    private Tienda tiendaQueOpera(Integer codtiSolicitado, Usuario usuario, String rol) {
        Integer codti = codtiSolicitado != null ? codtiSolicitado
                : (usuario.getTienda() != null ? usuario.getTienda().getCodti() : null);
        if (codti == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la tienda " + rol + ".");
        }
        validarOpera(usuario, codti);
        return tienda(codti);
    }

    private void validarDistintas(Tienda a, Tienda b) {
        if (a.getCodti().equals(b.getCodti())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La tienda origen y la destino no pueden ser la misma.");
        }
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    /** ROOT/ADMIN operan cualquier tienda; un encargado, solo la suya. */
    private boolean puedeOperar(Usuario u, Integer codti) {
        if (u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN) return true;
        return u.getRol() == Rol.ENCARGADO_TIENDA && u.getTienda() != null && u.getTienda().getCodti().equals(codti);
    }

    private void validarOpera(Usuario u, Integer codti) {
        if (!puedeOperar(u, codti)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para operar traspasos de la tienda " + codti + ".");
        }
    }

    // ── Mapeo ────────────────────────────────────────────────────────────────

    private TraspasoResponseDto toDto(Traspaso t) {
        // El código de cada renglón es de la tienda que tiene el producto: el origen en un envío, el destino en una solicitud.
        Integer tiendaDelProducto = t.getTipo() == TIPO_ENVIO ? t.getTiendaOrigen().getCodti() : t.getTiendaDestino().getCodti();

        Map<String, TraspasoLineaResponseDto> lineas = new LinkedHashMap<>();
        Map<String, List<String>> imeisPorCodigo = new LinkedHashMap<>();
        Map<String, BigDecimal> cantidadPorCodigo = new LinkedHashMap<>();
        for (TraspasoDetalle d : detalleRepository.findByTraspaso_IdtraspasoOrderByIddetalle(t.getIdtraspaso())) {
            cantidadPorCodigo.merge(d.getCodpro(), d.getCantidad(), BigDecimal::add);
            if (d.getImei() != null) imeisPorCodigo.computeIfAbsent(d.getCodpro(), k -> new ArrayList<>()).add(d.getImei());
        }
        for (Map.Entry<String, BigDecimal> e : cantidadPorCodigo.entrySet()) {
            Optional<Producto> p = productoRepository.findByCodproAndTienda_Codti(e.getKey(), tiendaDelProducto);
            lineas.put(e.getKey(), TraspasoLineaResponseDto.builder()
                    .codpro(e.getKey())
                    .nombreProducto(p.map(x -> x.getProductoMaster().getNombreBase()).orElse(null))
                    .tipoProducto(p.map(x -> x.getProductoMaster().getTipo().name()).orElse(null))
                    .cantidad(e.getValue())
                    .imeis(imeisPorCodigo.get(e.getKey()))
                    .build());
        }

        return TraspasoResponseDto.builder()
                .idtraspaso(t.getIdtraspaso())
                .tipo(t.getTipo())
                .tipoDisplay(t.getTipo() == TIPO_ENVIO ? "Envío" : "Solicitud")
                .codtiOrigen(t.getTiendaOrigen().getCodti())
                .nombreTiendaOrigen(t.getTiendaOrigen().getNombre())
                .codtiDestino(t.getTiendaDestino().getCodti())
                .nombreTiendaDestino(t.getTiendaDestino().getNombre())
                .idusuarioCrea(t.getUsuarioCrea().getIdusuario())
                .nombreCrea(t.getUsuarioCrea().getNombreCompleto())
                .idusuarioValida(t.getUsuarioValida() != null ? t.getUsuarioValida().getIdusuario() : null)
                .nombreValida(t.getUsuarioValida() != null ? t.getUsuarioValida().getNombreCompleto() : null)
                .estado(t.getEstado())
                .estadoDisplay(Boolean.TRUE.equals(t.getActivo()) ? estadoDisplay(t.getEstado()) : "Anulado")
                .activo(t.getActivo())
                .idtraspasoRef(t.getIdtraspasoRef())
                .fechaCreacion(t.getFechaCreacion())
                .fechaActualizacion(t.getFechaActualizacion())
                .motivoRechazo(t.getMotivoRechazo())
                .lineas(new ArrayList<>(lineas.values()))
                .build();
    }

    private String estadoDisplay(Byte estado) {
        return switch (estado) {
            case ENVIADO   -> "Enviado";
            case RECIBIDO  -> "Recibido";
            case LEIDO     -> "Leído";
            case ACEPTADA  -> "Aceptada";
            case RECHAZADA -> "Rechazada";
            default        -> "Desconocido";
        };
    }
}
