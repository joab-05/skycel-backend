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
    private static final String IMEI_FALTANTE    = "FALTANTE";
    private static final String IMEI_DE_BAJA     = "DEBAJA";

    private static final String ACCION_REPORTADO = "REPORTADO";
    private static final String ACCION_TARDE     = "RECIBIDO_TARDE";
    private static final String ACCION_REINTEGRO = "REINTEGRADO_ORIGEN";
    private static final String ACCION_BAJA      = "BAJA";

    private final TraspasoRepository        traspasoRepository;
    private final TraspasoDetalleRepository detalleRepository;
    private final TraspasoFaltanteMovRepository faltanteMovRepository;
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

    /** Recibe el envío completo. */
    @Transactional
    public TraspasoResponseDto recibir(Integer id, String username) {
        return recibir(id, null, username);
    }

    /**
     * La tienda destino confirma la recepción física: lo que llegó pasa a su inventario. Lo que no llegó (dicho en
     * {@code dto.faltantes}) queda como faltante por resolver: sigue descontado del origen, las unidades quedan FALTANTE y
     * se resuelve después (llegó tarde, regresó al origen o se da de baja).
     */
    @Transactional
    public TraspasoResponseDto recibir(Integer id, RecepcionRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Traspaso t = obtenerActivo(id);
        if (t.getTipo() != TIPO_ENVIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se recibe un envío; una solicitud se acepta o se rechaza.");
        }
        validarOpera(usuario, t.getTiendaDestino().getCodti());
        if (t.getEstado() != ENVIADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El envío " + id + " ya fue recibido.");
        }

        List<TraspasoDetalle> detalles = detalleRepository.findByTraspaso_IdtraspasoOrderByIddetalle(id);
        Map<Integer, BigDecimal> faltaPorDetalle = faltantesDeLaRecepcion(detalles, dto);
        boolean hayFaltantes = !faltaPorDetalle.isEmpty();
        String comentario = dto == null || dto.getComentario() == null ? "" : dto.getComentario().trim();
        if (hayFaltantes && comentario.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique en el comentario qué pasó con lo que no llegó.");
        }
        BigDecimal totalEnviado = detalles.stream().map(TraspasoDetalle::getCantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFalta = faltaPorDetalle.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalFalta.compareTo(totalEnviado) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No llegó nada del envío: si no se va a recibir, anúlelo desde la tienda origen.");
        }

        Map<Integer, Producto> destinoPorOrigen = new LinkedHashMap<>();
        for (TraspasoDetalle d : detalles) {
            BigDecimal falta = faltaPorDetalle.getOrDefault(d.getIddetalle(), BigDecimal.ZERO);
            BigDecimal llego = d.getCantidad().subtract(falta);
            d.setCantidadRecibida(llego);
            detalleRepository.save(d);

            if (falta.signum() > 0) {
                registrarMov(d, ACCION_REPORTADO, falta, usuario, comentario);
            }
            Producto enOrigen = productoDe(d.getCodpro(), t.getTiendaOrigen().getCodti(), "la tienda origen");
            Producto enDestino = null;
            if (llego.signum() > 0) {
                enDestino = destinoPorOrigen.computeIfAbsent(enOrigen.getIdproducto(),
                        k -> productoEnDestino(enOrigen, t.getTiendaDestino()));
                enDestino.setStock(stockDe(enDestino).add(llego));
            }
            if (d.getImei() != null) {
                ProductoImei pi = imeiDe(d);
                if (enDestino != null) {
                    pi.setProducto(enDestino);
                    pi.setEstado(IMEI_DISPONIBLE);
                } else {
                    pi.setEstado(IMEI_FALTANTE);
                }
                productoImeiRepository.save(pi);
            }
        }
        destinoPorOrigen.values().forEach(productoRepository::save);

        t.setEstado(RECIBIDO);
        t.setUsuarioValida(usuario);
        t.setConFaltantes(hayFaltantes);
        t.setComentarioRecepcion(comentario.isEmpty() ? null : comentario);
        return toDto(traspasoRepository.save(t));
    }

    /** Traduce lo que reportó la tienda (por código, cantidad o IMEI) a lo que falta de cada renglón. */
    private Map<Integer, BigDecimal> faltantesDeLaRecepcion(List<TraspasoDetalle> detalles, RecepcionRequestDto dto) {
        Map<Integer, BigDecimal> falta = new LinkedHashMap<>();
        if (dto == null || dto.getFaltantes() == null) return falta;
        Set<String> imeisYaReportados = new HashSet<>();
        for (RecepcionRequestDto.FaltanteDto f : dto.getFaltantes()) {
            String codpro = f.getCodpro() == null ? "" : f.getCodpro().trim();
            List<TraspasoDetalle> delProducto = detalles.stream().filter(d -> d.getCodpro().equals(codpro)).toList();
            if (delProducto.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto '" + codpro + "' no está en el envío.");
            }
            boolean esEquipo = delProducto.get(0).getImei() != null;
            if (esEquipo) {
                if (f.getImeis() == null || f.getImeis().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique los IMEI que no llegaron de '" + codpro + "'.");
                }
                for (String imei : f.getImeis()) {
                    TraspasoDetalle d = delProducto.stream().filter(x -> imei.equals(x.getImei())).findFirst()
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "El IMEI '" + imei + "' no viene en el envío como '" + codpro + "'."));
                    if (!imeisYaReportados.add(imei)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El IMEI '" + imei + "' está repetido.");
                    }
                    falta.put(d.getIddetalle(), d.getCantidad());
                }
            } else {
                if (f.getCantidad() == null || f.getCantidad().signum() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la cantidad que no llegó de '" + codpro + "'.");
                }
                BigDecimal restante = f.getCantidad();
                for (TraspasoDetalle d : delProducto) {
                    BigDecimal yaFalta = falta.getOrDefault(d.getIddetalle(), BigDecimal.ZERO);
                    BigDecimal toma = restante.min(d.getCantidad().subtract(yaFalta));
                    if (toma.signum() > 0) {
                        falta.put(d.getIddetalle(), yaFalta.add(toma));
                        restante = restante.subtract(toma);
                    }
                }
                if (restante.signum() > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Lo que no llegó de '" + codpro + "' es más de lo que se envió.");
                }
            }
        }
        return falta;
    }

    // ── Faltantes: resolver / consultar ──────────────────────────────────────

    /**
     * Resuelve (total o parcialmente) lo que faltó en un envío recibido:
     *  RECIBIDO_TARDE — llegó después; entra al inventario del destino (lo confirma el destino);
     *  REINTEGRADO_ORIGEN — nunca salió; regresa al inventario del origen (lo confirma el origen);
     *  BAJA — se perdió o se dañó; solo ROOT o ADMIN, con nota.
     */
    @Transactional
    public FaltanteResponseDto resolverFaltante(Integer iddetalle, ResolverFaltanteRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        TraspasoDetalle d = detalleRepository.findById(iddetalle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Renglón no encontrado: " + iddetalle));
        Traspaso t = d.getTraspaso();
        BigDecimal pendiente = pendienteDe(d);
        if (t.getTipo() != TIPO_ENVIO || !Boolean.TRUE.equals(t.getActivo()) || pendiente.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este renglón no tiene faltantes por resolver.");
        }
        String accion = dto.getAccion() == null ? "" : dto.getAccion().trim().toUpperCase();
        BigDecimal cantidad = dto.getCantidad() != null ? dto.getCantidad() : pendiente;
        if (cantidad.signum() <= 0 || cantidad.compareTo(pendiente) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a 0 y no exceder lo pendiente (" + pendiente + ").");
        }
        if (d.getImei() != null && cantidad.compareTo(d.getCantidad()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un equipo se resuelve completo (1 unidad).");
        }
        String nota = dto.getNota() == null ? null : dto.getNota().trim();
        Producto enOrigen = productoDe(d.getCodpro(), t.getTiendaOrigen().getCodti(), "la tienda origen");

        switch (accion) {
            case ACCION_TARDE -> {
                validarOpera(usuario, t.getTiendaDestino().getCodti());
                Producto enDestino = productoEnDestino(enOrigen, t.getTiendaDestino());
                enDestino.setStock(stockDe(enDestino).add(cantidad));
                productoRepository.save(enDestino);
                d.setCantidadRecibida(d.getCantidadRecibida().add(cantidad));
                if (d.getImei() != null) {
                    ProductoImei pi = imeiDe(d);
                    pi.setProducto(enDestino);
                    pi.setEstado(IMEI_DISPONIBLE);
                    productoImeiRepository.save(pi);
                }
            }
            case ACCION_REINTEGRO -> {
                validarOpera(usuario, t.getTiendaOrigen().getCodti());
                enOrigen.setStock(stockDe(enOrigen).add(cantidad));
                productoRepository.save(enOrigen);
                d.setCantidadReintegrada(d.getCantidadReintegrada().add(cantidad));
                if (d.getImei() != null) {
                    ProductoImei pi = imeiDe(d);
                    pi.setEstado(IMEI_DISPONIBLE);
                    productoImeiRepository.save(pi);
                }
            }
            case ACCION_BAJA -> {
                if (usuario.getRol() != Rol.ROOT && usuario.getRol() != Rol.ADMIN) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede dar de baja un faltante.");
                }
                if (nota == null || nota.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique en la nota el motivo de la baja.");
                }
                d.setCantidadBaja(d.getCantidadBaja().add(cantidad));
                if (d.getImei() != null) {
                    ProductoImei pi = imeiDe(d);
                    pi.setEstado(IMEI_DE_BAJA);
                    productoImeiRepository.save(pi);
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Acción no válida. Use RECIBIDO_TARDE, REINTEGRADO_ORIGEN o BAJA.");
        }
        detalleRepository.save(d);
        registrarMov(d, accion, cantidad, usuario, nota);
        return toFaltanteDto(d);
    }

    /** Faltantes sin resolver. Un administrador ve todas las tiendas (o una); un encargado, solo los de la suya. */
    @Transactional(readOnly = true)
    public List<FaltanteResponseDto> listarFaltantes(Integer codti, String username) {
        Usuario usuario = usuarioPorUsername(username);
        boolean admin = usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN;
        Integer filtro = codti;
        if (!admin) {
            Integer suya = usuario.getTienda() != null ? usuario.getTienda().getCodti() : null;
            if (suya == null || (codti != null && !codti.equals(suya))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a los faltantes de otras tiendas.");
            }
            filtro = suya;
        }
        return detalleRepository.faltantesPendientes(filtro).stream().map(this::toFaltanteDto).collect(Collectors.toList());
    }

    /** Lo que sigue sin resolverse de un renglón (0 si el envío se recibió completo). */
    private BigDecimal pendienteDe(TraspasoDetalle d) {
        if (d.getCantidadRecibida() == null || !Boolean.TRUE.equals(d.getTraspaso().getConFaltantes())) return BigDecimal.ZERO;
        return d.getCantidad().subtract(d.getCantidadRecibida()).subtract(d.getCantidadBaja()).subtract(d.getCantidadReintegrada());
    }

    private ProductoImei imeiDe(TraspasoDetalle d) {
        return productoImeiRepository.findByImei(d.getImei())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "La unidad '" + d.getImei() + "' del envío ya no existe."));
    }

    private void registrarMov(TraspasoDetalle d, String accion, BigDecimal cantidad, Usuario usuario, String nota) {
        faltanteMovRepository.save(TraspasoFaltanteMov.builder()
                .detalle(d).accion(accion).cantidad(cantidad).usuario(usuario)
                .nota(nota == null || nota.isEmpty() ? null : nota).build());
    }

    private FaltanteResponseDto toFaltanteDto(TraspasoDetalle d) {
        Traspaso t = d.getTraspaso();
        Optional<Producto> p = productoRepository.findByCodproAndTienda_Codti(d.getCodpro(), t.getTiendaOrigen().getCodti());
        return FaltanteResponseDto.builder()
                .iddetalle(d.getIddetalle()).idtraspaso(t.getIdtraspaso())
                .codtiOrigen(t.getTiendaOrigen().getCodti()).nombreTiendaOrigen(t.getTiendaOrigen().getNombre())
                .codtiDestino(t.getTiendaDestino().getCodti()).nombreTiendaDestino(t.getTiendaDestino().getNombre())
                .codpro(d.getCodpro())
                .nombreProducto(p.map(x -> x.getProductoMaster().getNombreBase()).orElse(null))
                .imei(d.getImei())
                .cantidadEnviada(d.getCantidad()).cantidadRecibida(d.getCantidadRecibida())
                .cantidadBaja(d.getCantidadBaja()).cantidadReintegrada(d.getCantidadReintegrada())
                .pendiente(pendienteDe(d))
                .comentarioRecepcion(t.getComentarioRecepcion())
                .historial(faltanteMovRepository.findByDetalle_IddetalleOrderByIdmov(d.getIddetalle()).stream()
                        .map(m -> FaltanteResponseDto.MovimientoDto.builder().accion(m.getAccion()).cantidad(m.getCantidad())
                                .usuario(m.getUsuario().getNombreCompleto()).nota(m.getNota()).fecha(m.getFecha()).build())
                        .collect(Collectors.toList()))
                .build();
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
        Map<String, BigDecimal> recibidaPorCodigo = new LinkedHashMap<>();
        Map<String, BigDecimal> pendientePorCodigo = new LinkedHashMap<>();
        for (TraspasoDetalle d : detalleRepository.findByTraspaso_IdtraspasoOrderByIddetalle(t.getIdtraspaso())) {
            cantidadPorCodigo.merge(d.getCodpro(), d.getCantidad(), BigDecimal::add);
            if (t.getTipo() == TIPO_ENVIO && t.getEstado() == RECIBIDO) {
                BigDecimal recibida = d.getCantidadRecibida() != null ? d.getCantidadRecibida() : d.getCantidad();
                recibidaPorCodigo.merge(d.getCodpro(), recibida, BigDecimal::add);
                pendientePorCodigo.merge(d.getCodpro(), pendienteDe(d), BigDecimal::add);
            }
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
                    .cantidadRecibida(recibidaPorCodigo.get(e.getKey()))
                    .faltantePendiente(pendientePorCodigo.get(e.getKey()))
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
                .conFaltantes(Boolean.TRUE.equals(t.getConFaltantes()))
                .comentarioRecepcion(t.getComentarioRecepcion())
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
