package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.servicios.*;
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
 * Corte diario de servicios: recargas, pagos de servicios, pines electrónicos y pagos PayJoy, que se operan en los sistemas
 * de cada proveedor y no pasan por las ventas ni por la caja.
 *
 * El encargado captura al cierre, por servicio, cuántas operaciones y cuánto importaron (y, si quiere, los saldos del proveedor).
 * La comisión se calcula: operaciones × comisión del servicio (p. ej. $15 en pagos de servicios y pines). Un administrador
 * confirma el corte con lo que reporta cada proveedor: sin diferencias queda Confirmado; con diferencias, exige un comentario.
 */
@Service
@RequiredArgsConstructor
public class CorteServiciosService {

    public static final byte CAPTURADO = 1;
    public static final byte CONFIRMADO = 2;
    public static final byte CON_DIFERENCIAS = 3;

    private record TipoBase(String nombre, String comision, int orden) {}

    private static final List<TipoBase> TIPOS_BASE = List.of(
            new TipoBase("Recargas", "0", 1),
            new TipoBase("Pagos de servicios", "15", 2),
            new TipoBase("Pines electrónicos", "15", 3),
            new TipoBase("Pagos PayJoy", "0", 4));

    private final ServicioTipoRepository          tipoRepository;
    private final CorteServiciosRepository        corteRepository;
    private final CorteServiciosLineaRepository   lineaRepository;
    private final TiendaRepository                tiendaRepository;
    private final UsuarioRepository               usuarioRepository;

    // ── Catálogo de servicios ────────────────────────────────────────────────

    /** Crea los servicios base si el catálogo está vacío. */
    @Transactional
    public void asegurarTiposBase() {
        if (tipoRepository.count() > 0) return;
        for (TipoBase t : TIPOS_BASE) {
            tipoRepository.save(ServicioTipo.builder().nombre(t.nombre()).comisionPorOperacion(new BigDecimal(t.comision()))
                    .orden(t.orden()).activo(true).build());
        }
    }

    @Transactional(readOnly = true)
    public List<ServicioTipoDto> tipos(boolean incluirInactivos) {
        List<ServicioTipo> lista = incluirInactivos ? tipoRepository.findAllByOrderByOrdenAscIdtipoAsc()
                : tipoRepository.findByActivoTrueOrderByOrdenAscIdtipoAsc();
        return lista.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ServicioTipoDto crearTipo(ServicioTipoDto dto) {
        String nombre = dto.getNombre().trim();
        if (tipoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un servicio llamado «" + nombre + "».");
        }
        int orden = dto.getOrden() != null ? dto.getOrden() : (int) tipoRepository.count() + 1;
        return toDto(tipoRepository.save(ServicioTipo.builder().nombre(nombre).comisionPorOperacion(dto.getComisionPorOperacion())
                .orden(orden).activo(true).build()));
    }

    /** Cambia nombre, comisión, orden o si está activo. Los cortes ya capturados conservan la comisión con la que se capturaron. */
    @Transactional
    public ServicioTipoDto actualizarTipo(Integer id, ServicioTipoDto dto) {
        ServicioTipo t = tipoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Servicio no encontrado: " + id));
        String nombre = dto.getNombre().trim();
        if (!nombre.equalsIgnoreCase(t.getNombre()) && tipoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un servicio llamado «" + nombre + "».");
        }
        t.setNombre(nombre);
        t.setComisionPorOperacion(dto.getComisionPorOperacion());
        if (dto.getOrden() != null) t.setOrden(dto.getOrden());
        if (dto.getActivo() != null) t.setActivo(dto.getActivo());
        return toDto(tipoRepository.save(t));
    }

    // ── Capturar el corte ────────────────────────────────────────────────────

    /** Guarda el corte del día de una sucursal; si ya había uno sin confirmar, lo reemplaza. */
    @Transactional
    public CorteServiciosResponseDto guardar(CorteServiciosRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Tienda tienda = tiendaQueOpera(dto.getCodti(), usuario);
        LocalDate fecha = dto.getFecha() != null ? dto.getFecha() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede hacer el corte de un día futuro.");
        }

        Optional<CorteServicios> existente = corteRepository.findByTienda_CodtiAndFecha(tienda.getCodti(), fecha);
        if (existente.isPresent() && existente.get().getEstado() != CAPTURADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El corte del " + fecha + " de " + tienda.getNombre() + " ya fue confirmado. Un administrador debe reabrirlo para corregirlo.");
        }

        // Validar y calcular antes de tocar nada
        Set<Integer> vistos = new HashSet<>();
        List<CorteServiciosLinea> nuevas = new ArrayList<>();
        for (CorteServiciosRequestDto.LineaDto l : dto.getLineas()) {
            ServicioTipo tipo = tipoRepository.findById(l.getIdtipo())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Servicio no encontrado: " + l.getIdtipo()));
            if (!Boolean.TRUE.equals(tipo.getActivo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El servicio «" + tipo.getNombre() + "» está desactivado.");
            }
            if (!vistos.add(tipo.getIdtipo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El servicio «" + tipo.getNombre() + "» está repetido.");
            }
            int operaciones = l.getOperaciones() != null ? l.getOperaciones() : 0;
            boolean cobraComision = tipo.getComisionPorOperacion().signum() > 0;
            if (cobraComision && l.getMonto().signum() > 0 && operaciones == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Indique cuántas operaciones hubo de «" + tipo.getNombre() + "»: la comisión se cobra por operación.");
            }
            nuevas.add(CorteServiciosLinea.builder().tipo(tipo).nombreTipo(tipo.getNombre())
                    .comisionPorOperacion(tipo.getComisionPorOperacion()).operaciones(operaciones).monto(l.getMonto())
                    .comision(tipo.getComisionPorOperacion().multiply(BigDecimal.valueOf(operaciones)))
                    .saldoInicial(l.getSaldoInicial()).fondeo(l.getFondeo()).saldoFinal(l.getSaldoFinal()).build());
        }

        CorteServicios corte;
        if (existente.isPresent()) {
            corte = existente.get();
            lineaRepository.deleteByCorte_Idcorte(corte.getIdcorte());
            corte.setUsuarioCaptura(usuario);
        } else {
            corte = CorteServicios.builder().tienda(tienda).fecha(fecha).estado(CAPTURADO).usuarioCaptura(usuario).build();
        }
        corte.setObservaciones(limpio(dto.getObservaciones()));
        corte = corteRepository.save(corte);
        for (CorteServiciosLinea l : nuevas) {
            l.setCorte(corte);
            lineaRepository.save(l);
        }
        return toDto(corte);
    }

    // ── Confirmar / reabrir (administrador) ──────────────────────────────────

    @Transactional
    public CorteServiciosResponseDto confirmar(Integer id, ConfirmarCorteRequestDto dto, String username) {
        Usuario usuario = usuarioPorUsername(username);
        exigirSuperior(usuario, "Solo un administrador confirma los cortes.");
        CorteServicios corte = corte(id);
        if (corte.getEstado() != CAPTURADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El corte ya fue confirmado.");
        }
        List<CorteServiciosLinea> lineas = lineaRepository.findByCorte_IdcorteOrderByIdlinea(id);
        Map<Integer, BigDecimal> reportado = new HashMap<>();
        for (ConfirmarCorteRequestDto.LineaDto l : dto.getLineas()) {
            if (reportado.put(l.getIdlinea(), l.getMontoProveedor()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El renglón " + l.getIdlinea() + " está repetido.");
            }
        }
        boolean hayDiferencias = false;
        for (CorteServiciosLinea l : lineas) {
            BigDecimal m = reportado.get(l.getIdlinea());
            if (m == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta lo que reporta el proveedor de «" + l.getNombreTipo() + "».");
            }
            if (l.getMonto().compareTo(m) != 0) hayDiferencias = true;
        }
        if (!lineas.stream().map(CorteServiciosLinea::getIdlinea).collect(Collectors.toSet()).containsAll(reportado.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay renglones que no pertenecen a este corte.");
        }
        String comentario = limpio(dto.getComentario());
        if (hayDiferencias && comentario == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay diferencias con lo que reportan los proveedores: indique en el comentario a qué se deben.");
        }
        for (CorteServiciosLinea l : lineas) {
            l.setMontoProveedor(reportado.get(l.getIdlinea()));
            lineaRepository.save(l);
        }
        corte.setEstado(hayDiferencias ? CON_DIFERENCIAS : CONFIRMADO);
        corte.setUsuarioConfirma(usuario);
        corte.setFechaConfirmacion(LocalDateTime.now());
        corte.setComentarioConfirmacion(comentario);
        return toDto(corteRepository.save(corte));
    }

    /** Vuelve el corte a Capturado para poder corregirlo. */
    @Transactional
    public CorteServiciosResponseDto reabrir(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        exigirSuperior(usuario, "Solo un administrador reabre los cortes.");
        CorteServicios corte = corte(id);
        if (corte.getEstado() == CAPTURADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El corte no está confirmado.");
        }
        for (CorteServiciosLinea l : lineaRepository.findByCorte_IdcorteOrderByIdlinea(id)) {
            l.setMontoProveedor(null);
            lineaRepository.save(l);
        }
        corte.setEstado(CAPTURADO);
        corte.setUsuarioConfirma(null);
        corte.setFechaConfirmacion(null);
        corte.setComentarioConfirmacion(null);
        return toDto(corteRepository.save(corte));
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CorteServiciosResponseDto> listar(Integer codti, LocalDate desde, LocalDate hasta, Byte estado, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Integer filtro = filtroTienda(usuario, codti);
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.minusDays(30);
        return corteRepository.buscar(filtro, ini, fin, estado).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CorteServiciosResponseDto obtener(Integer id, String username) {
        Usuario usuario = usuarioPorUsername(username);
        CorteServicios c = corte(id);
        exigirOperaTienda(usuario, c.getTienda().getCodti());
        return toDto(c);
    }

    /** El corte de un día de una sucursal, o null si aún no se ha capturado (para precargar la captura). */
    @Transactional(readOnly = true)
    public CorteServiciosResponseDto delDia(Integer codti, LocalDate fecha, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Tienda tienda = tiendaQueOpera(codti, usuario);
        return corteRepository.findByTienda_CodtiAndFecha(tienda.getCodti(), fecha != null ? fecha : LocalDate.now())
                .map(this::toDto).orElse(null);
    }

    /** Totales por servicio de los cortes de un periodo. */
    @Transactional(readOnly = true)
    public ResumenServiciosResponseDto resumen(Integer codti, LocalDate desde, LocalDate hasta, String username) {
        Usuario usuario = usuarioPorUsername(username);
        Integer filtro = filtroTienda(usuario, codti);
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.withDayOfMonth(1);
        List<CorteServicios> cortes = corteRepository.buscar(filtro, ini, fin, null);

        Map<String, int[]> ops = new LinkedHashMap<>();
        Map<String, BigDecimal[]> importes = new LinkedHashMap<>();
        BigDecimal totalMonto = BigDecimal.ZERO, totalComision = BigDecimal.ZERO;
        for (CorteServicios c : cortes) {
            for (CorteServiciosLinea l : lineaRepository.findByCorte_IdcorteOrderByIdlinea(c.getIdcorte())) {
                ops.computeIfAbsent(l.getNombreTipo(), k -> new int[1])[0] += l.getOperaciones();
                BigDecimal[] v = importes.computeIfAbsent(l.getNombreTipo(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                v[0] = v[0].add(l.getMonto());
                v[1] = v[1].add(l.getComision());
                totalMonto = totalMonto.add(l.getMonto());
                totalComision = totalComision.add(l.getComision());
            }
        }
        List<ResumenServiciosResponseDto.TipoDto> porServicio = new ArrayList<>();
        importes.forEach((nombre, v) -> porServicio.add(ResumenServiciosResponseDto.TipoDto.builder()
                .nombre(nombre).operaciones(ops.get(nombre)[0]).monto(v[0]).comision(v[1]).build()));
        return ResumenServiciosResponseDto.builder().desde(ini).hasta(fin).cortes(cortes.size())
                .cortesSinConfirmar((int) cortes.stream().filter(c -> c.getEstado() == CAPTURADO).count())
                .cortesConDiferencias((int) cortes.stream().filter(c -> c.getEstado() == CON_DIFERENCIAS).count())
                .totalMonto(totalMonto).totalComision(totalComision).porServicio(porServicio).build();
    }

    // ── Permisos ─────────────────────────────────────────────────────────────

    private boolean esSuperior(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    private void exigirSuperior(Usuario u, String mensaje) {
        if (!esSuperior(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensaje);
    }

    private boolean puedeOperar(Usuario u, Integer codti) {
        return esSuperior(u) || (u.getRol() == Rol.ENCARGADO_TIENDA && u.getTienda() != null && u.getTienda().getCodti().equals(codti));
    }

    private void exigirOperaTienda(Usuario u, Integer codti) {
        if (!puedeOperar(u, codti)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permiso para los cortes de servicios de la sucursal " + codti + ".");
        }
    }

    private Tienda tiendaQueOpera(Integer codtiSolicitado, Usuario usuario) {
        Integer codti = codtiSolicitado != null ? codtiSolicitado : (usuario.getTienda() != null ? usuario.getTienda().getCodti() : null);
        if (codti == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la sucursal (codti).");
        exigirOperaTienda(usuario, codti);
        return tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no encontrada: " + codti));
    }

    /** Un administrador consulta todas (o una); un encargado, solo la suya. */
    private Integer filtroTienda(Usuario u, Integer codti) {
        if (esSuperior(u)) return codti;
        Integer suya = u.getRol() == Rol.ENCARGADO_TIENDA && u.getTienda() != null ? u.getTienda().getCodti() : null;
        if (suya == null || (codti != null && !codti.equals(suya))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede consultar los cortes de su sucursal.");
        }
        return suya;
    }

    private CorteServicios corte(Integer id) {
        return corteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Corte no encontrado: " + id));
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ── Mapeo ────────────────────────────────────────────────────────────────

    private ServicioTipoDto toDto(ServicioTipo t) {
        return ServicioTipoDto.builder().idtipo(t.getIdtipo()).nombre(t.getNombre())
                .comisionPorOperacion(t.getComisionPorOperacion()).orden(t.getOrden()).activo(t.getActivo()).build();
    }

    private CorteServiciosResponseDto toDto(CorteServicios c) {
        List<CorteServiciosLinea> lineas = lineaRepository.findByCorte_IdcorteOrderByIdlinea(c.getIdcorte());
        boolean confirmado = c.getEstado() != CAPTURADO;
        BigDecimal totalMonto = BigDecimal.ZERO, totalComision = BigDecimal.ZERO, totalProveedor = BigDecimal.ZERO;
        List<CorteServiciosResponseDto.LineaDto> dtos = new ArrayList<>();
        for (CorteServiciosLinea l : lineas) {
            totalMonto = totalMonto.add(l.getMonto());
            totalComision = totalComision.add(l.getComision());
            BigDecimal consumo = l.getSaldoInicial() != null && l.getSaldoFinal() != null
                    ? l.getSaldoInicial().add(l.getFondeo() != null ? l.getFondeo() : BigDecimal.ZERO).subtract(l.getSaldoFinal()) : null;
            if (l.getMontoProveedor() != null) totalProveedor = totalProveedor.add(l.getMontoProveedor());
            dtos.add(CorteServiciosResponseDto.LineaDto.builder().idlinea(l.getIdlinea()).idtipo(l.getTipo().getIdtipo())
                    .nombre(l.getNombreTipo()).comisionPorOperacion(l.getComisionPorOperacion()).operaciones(l.getOperaciones())
                    .monto(l.getMonto()).comision(l.getComision()).saldoInicial(l.getSaldoInicial()).fondeo(l.getFondeo())
                    .saldoFinal(l.getSaldoFinal()).consumoSaldo(consumo)
                    .diferenciaSaldo(consumo != null ? consumo.subtract(l.getMonto()) : null)
                    .montoProveedor(l.getMontoProveedor())
                    .diferencia(l.getMontoProveedor() != null ? l.getMonto().subtract(l.getMontoProveedor()) : null).build());
        }
        return CorteServiciosResponseDto.builder()
                .idcorte(c.getIdcorte()).codti(c.getTienda().getCodti()).nombreTienda(c.getTienda().getNombre()).fecha(c.getFecha())
                .estado(c.getEstado()).estadoDisplay(switch (c.getEstado()) { case CAPTURADO -> "Capturado"; case CONFIRMADO -> "Confirmado"; default -> "Con diferencias"; })
                .nombreCaptura(c.getUsuarioCaptura().getNombreCompleto()).fechaCaptura(c.getFechaCaptura()).observaciones(c.getObservaciones())
                .nombreConfirma(c.getUsuarioConfirma() != null ? c.getUsuarioConfirma().getNombreCompleto() : null)
                .fechaConfirmacion(c.getFechaConfirmacion()).comentarioConfirmacion(c.getComentarioConfirmacion())
                .totalMonto(totalMonto).totalComision(totalComision)
                .totalProveedor(confirmado ? totalProveedor : null).totalDiferencia(confirmado ? totalMonto.subtract(totalProveedor) : null)
                .lineas(dtos).build();
    }
}
