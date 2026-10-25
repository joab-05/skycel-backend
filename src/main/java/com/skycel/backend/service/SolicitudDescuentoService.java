package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.SolicitudDescuento;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.descuento.SolicitudDescuentoRequestDto;
import com.skycel.backend.repository.ProductoMasterRepository;
import com.skycel.backend.repository.SolicitudDescuentoRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Descuentos puntuales que un vendedor o encargado pide a un administrador mientras arma una venta. El administrador aprueba (con el
 * monto pedido u otro) o rechaza; la aprobación sirve una sola vez, para ese artículo y esa sucursal, durante unas horas, y
 * {@link VentaService} la valida al cobrar.
 */
@Service
@RequiredArgsConstructor
public class SolicitudDescuentoService {

    /** Cuánto tiempo sirve una aprobación para cobrarla. */
    static final long HORAS_DE_VIGENCIA = 12;

    private final SolicitudDescuentoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final TiendaRepository tiendaRepository;
    private final ProductoMasterRepository productoMasterRepository;
    private final NotificacionService notificacionService;

    // ── Pedir ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> crear(SolicitudDescuentoRequestDto dto, String username) {
        Usuario usuario = usuario(username);
        if (dto.getCodti() == null || dto.getIdprodmaster() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la sucursal y el artículo.");
        if (dto.getMotivo() == null || dto.getMotivo().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el motivo del descuento.");
        if (dto.getPrecioLista() == null || dto.getPrecioLista().signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El artículo no tiene precio.");
        if (dto.getDescuentoPorPieza() == null || dto.getDescuentoPorPieza().signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica cuánto descuento pides.");
        if (dto.getDescuentoPorPieza().compareTo(dto.getPrecioLista()) >= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento debe ser menor al precio del artículo.");
        short cantidad = dto.getCantidad() == null || dto.getCantidad() < 1 ? 1 : dto.getCantidad();
        Tienda tienda = tiendaRepository.findById(dto.getCodti())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sucursal no encontrada."));
        ProductoMaster master = productoMasterRepository.findById(dto.getIdprodmaster())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artículo no encontrado."));

        SolicitudDescuento s = repository.save(SolicitudDescuento.builder()
                .tienda(tienda).solicitante(usuario).productoMaster(master)
                .imei(dto.getImei() == null || dto.getImei().isBlank() ? null : dto.getImei().trim().toUpperCase())
                .codpro(dto.getCodpro() == null || dto.getCodpro().isBlank() ? null : dto.getCodpro().trim())
                .cantidad(cantidad).precioLista(dto.getPrecioLista()).descuentoPorPieza(dto.getDescuentoPorPieza())
                .motivo(dto.getMotivo().trim()).estado(SolicitudDescuento.PENDIENTE).build());

        String titulo = "Solicitud de descuento de " + nombre(usuario) + " en " + tienda.getNombre() + ": "
                + master.getNombreBase() + " (−$" + dto.getDescuentoPorPieza().stripTrailingZeros().toPlainString() + ")";
        notificarAdministradores(titulo);
        return aMapa(s);
    }

    // ── Consultar ─────────────────────────────────────────────────────────────

    /** Las solicitudes pendientes de resolver (para quien las aprueba). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendientes() {
        return repository.findByEstadoOrderByFechaSolicitudAsc(SolicitudDescuento.PENDIENTE).stream()
                .map(this::aMapa).collect(Collectors.toList());
    }

    /** Una solicitud: la ve quien la pidió o un administrador. */
    @Transactional(readOnly = true)
    public Map<String, Object> obtener(Integer id, String username) {
        Usuario usuario = usuario(username);
        SolicitudDescuento s = buscar(id);
        if (!esAdmin(usuario) && !s.getSolicitante().getIdusuario().equals(usuario.getIdusuario()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes ver tus propias solicitudes.");
        return aMapa(s);
    }

    // ── Resolver ──────────────────────────────────────────────────────────────

    /** @param descuentoPorPieza el monto que se autoriza; null = el que se pidió */
    @Transactional
    public Map<String, Object> aprobar(Integer id, BigDecimal descuentoPorPieza, String username) {
        Usuario admin = usuario(username);
        SolicitudDescuento s = buscar(id);
        if (s.getEstado() != SolicitudDescuento.PENDIENTE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa solicitud ya se resolvió.");
        BigDecimal monto = descuentoPorPieza != null ? descuentoPorPieza : s.getDescuentoPorPieza();
        if (monto.signum() <= 0 || monto.compareTo(s.getPrecioLista()) >= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento debe ser mayor a $0 y menor al precio del artículo.");
        s.setDescuentoPorPieza(monto);
        s.setEstado(SolicitudDescuento.APROBADA);
        s.setResolvio(admin);
        s.setFechaResolucion(LocalDateTime.now());
        repository.save(s);
        notificacionService.notificarUsuario(s.getSolicitante(), "DESCUENTO_APROBADO",
                "Descuento aprobado: " + s.getProductoMaster().getNombreBase() + " (−$" + monto.stripTrailingZeros().toPlainString() + " c/u)", "#/vender");
        return aMapa(s);
    }

    @Transactional
    public Map<String, Object> rechazar(Integer id, String motivo, String username) {
        Usuario admin = usuario(username);
        SolicitudDescuento s = buscar(id);
        if (s.getEstado() != SolicitudDescuento.PENDIENTE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa solicitud ya se resolvió.");
        s.setEstado(SolicitudDescuento.RECHAZADA);
        s.setResolvio(admin);
        s.setMotivoResolucion(motivo == null || motivo.isBlank() ? null : motivo.trim());
        s.setFechaResolucion(LocalDateTime.now());
        repository.save(s);
        notificacionService.notificarUsuario(s.getSolicitante(), "DESCUENTO_RECHAZADO",
                "Descuento rechazado: " + s.getProductoMaster().getNombreBase()
                        + (s.getMotivoResolucion() != null ? " — " + s.getMotivoResolucion() : ""), "#/vender");
        return aMapa(s);
    }

    /** Quien la pidió la retira (por ejemplo, si quita el artículo del carrito). */
    @Transactional
    public Map<String, Object> cancelar(Integer id, String username) {
        Usuario usuario = usuario(username);
        SolicitudDescuento s = buscar(id);
        if (!s.getSolicitante().getIdusuario().equals(usuario.getIdusuario()) && !esAdmin(usuario))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien la pidió puede cancelarla.");
        if (s.getEstado() == SolicitudDescuento.PENDIENTE || s.getEstado() == SolicitudDescuento.APROBADA) {
            s.setEstado(SolicitudDescuento.CANCELADA);
            s.setFechaResolucion(LocalDateTime.now());
            repository.save(s);
        }
        return aMapa(s);
    }

    // ── Uso en la venta ───────────────────────────────────────────────────────

    /**
     * La solicitud aprobada que respalda un descuento al cobrar. Falla si no es del vendedor, de la sucursal o del artículo, si ya se
     * usó, no está aprobada o venció.
     */
    @Transactional(readOnly = true)
    public SolicitudDescuento validarParaVenta(Integer id, Usuario vendedor, Integer codti, ProductoMaster master, String imei) {
        SolicitudDescuento s = buscar(id);
        if (s.getEstado() != SolicitudDescuento.APROBADA)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento de '" + master.getNombreBase() + "' "
                    + (s.getEstado() == SolicitudDescuento.USADA ? "ya se usó en otra venta." : "no está aprobado."));
        if (!s.getSolicitante().getIdusuario().equals(vendedor.getIdusuario()) || !s.getTienda().getCodti().equals(codti)
                || !s.getProductoMaster().getIdprodmaster().equals(master.getIdprodmaster()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento aprobado no corresponde a este artículo, vendedor o sucursal.");
        if (s.getImei() != null && (imei == null || !s.getImei().equalsIgnoreCase(imei.trim())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento aprobado es para otro equipo (IMEI " + s.getImei() + ").");
        if (s.getFechaResolucion() != null && s.getFechaResolucion().plusHours(HORAS_DE_VIGENCIA).isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El descuento aprobado de '" + master.getNombreBase() + "' ya venció: pídelo de nuevo.");
        return s;
    }

    @Transactional
    public void marcarUsada(Integer id, Integer idventa) {
        SolicitudDescuento s = buscar(id);
        s.setEstado(SolicitudDescuento.USADA);
        s.setIdventa(idventa);
        repository.save(s);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────

    private void notificarAdministradores(String titulo) {
        notificacionService.notificarRol(Rol.ADMIN, "DESCUENTO_SOLICITUD", titulo, "#/descuentos");
        notificacionService.notificarRol(Rol.ROOT, "DESCUENTO_SOLICITUD", titulo, "#/descuentos");
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado."));
    }

    private SolicitudDescuento buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud de descuento no encontrada: " + id));
    }

    private static boolean esAdmin(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    private static String nombre(Usuario u) {
        return u.getNombreCompleto() != null && !u.getNombreCompleto().isBlank() ? u.getNombreCompleto() : u.getUsername();
    }

    private Map<String, Object> aMapa(SolicitudDescuento s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idsolicitud", s.getIdsolicitud());
        m.put("codti", s.getTienda().getCodti());
        m.put("nombreTienda", s.getTienda().getNombre());
        m.put("solicitante", nombre(s.getSolicitante()));
        m.put("idprodmaster", s.getProductoMaster().getIdprodmaster());
        m.put("articulo", s.getProductoMaster().getNombreBase());
        m.put("imei", s.getImei());
        m.put("codpro", s.getCodpro());
        m.put("cantidad", s.getCantidad());
        m.put("precioLista", s.getPrecioLista());
        m.put("descuentoPorPieza", s.getDescuentoPorPieza());
        m.put("motivo", s.getMotivo());
        m.put("estado", s.getEstado());
        m.put("estadoDisplay", switch (s.getEstado()) {
            case SolicitudDescuento.PENDIENTE -> "Pendiente";
            case SolicitudDescuento.APROBADA -> "Aprobada";
            case SolicitudDescuento.RECHAZADA -> "Rechazada";
            case SolicitudDescuento.USADA -> "Usada";
            default -> "Cancelada";
        });
        m.put("motivoResolucion", s.getMotivoResolucion());
        m.put("resolvio", s.getResolvio() != null ? nombre(s.getResolvio()) : null);
        m.put("fechaSolicitud", s.getFechaSolicitud());
        m.put("fechaResolucion", s.getFechaResolucion());
        return m;
    }
}
