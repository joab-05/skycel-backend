package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EmpleadoDescanso;
import com.skycel.backend.domain.entity.EmpleadoDescansoAjuste;
import com.skycel.backend.domain.entity.EmpleadoDescansoSaldo;
import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.EmpleadoDescansoAjusteRepository;
import com.skycel.backend.repository.EmpleadoDescansoRepository;
import com.skycel.backend.repository.EmpleadoDescansoSaldoRepository;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Descansos: la bitácora de fechas tomadas (ya existía) más un saldo semi-automático por empleado. Un
 * administrador fija un saldo inicial a partir de una fecha de corte; de ahí en adelante el sistema suma
 * solo los días que se van ganando según la categoría (1 por semana para todos; +1 por quincena para
 * administrativos y encargados de las tiendas grandes) y resta los descansos tomados. Los ajustes manuales
 * (vacaciones al año, salud, correcciones) se registran aparte y se suman al saldo — el negocio tiene
 * demasiadas excepciones cotidianas como para que una fórmula fija baste por sí sola.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoDescansoService {

    public static final String CAT_ADMINISTRATIVO = "ADMINISTRATIVO";
    public static final String CAT_ENCARGADO_TOP = "ENCARGADO_TOP";
    public static final String CAT_ENCARGADO_APOYO = "ENCARGADO_APOYO";

    /** Zócalo, Superche y Corpo: las tiendas grandes cuyo encargado tiene la mejor categoría. */
    private static final Set<Integer> TIENDAS_ENCARGADO_TOP = Set.of(2, 4, 5);

    private static final Map<String, Integer> MAXIMO_POR_CATEGORIA = Map.of(
            CAT_ADMINISTRATIVO, 4,
            CAT_ENCARGADO_TOP, 4,
            CAT_ENCARGADO_APOYO, 2
    );

    private final EmpleadoDescansoRepository descansoRepository;
    private final EmpleadoDescansoSaldoRepository saldoRepository;
    private final EmpleadoDescansoAjusteRepository ajusteRepository;
    private final EmpleadoPerfilRepository empleadoPerfilRepository;
    private final UsuarioRepository usuarioRepository;

    // ── Bitácora de fechas tomadas (ya existía) ────────────────────────────────

    @Transactional
    public Map<String, Object> registrar(Integer idempleado, LocalDate fecha, String observaciones, String username) {
        EmpleadoPerfil empleado = empleadoDe(idempleado);
        validarAcceso(usuarioPorUsername(username), empleado);
        if (fecha == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la fecha del descanso.");
        if (descansoRepository.existsByEmpleado_IdempleadoAndFecha(idempleado, fecha))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay un descanso registrado para ese empleado en esa fecha.");

        EmpleadoDescanso d = descansoRepository.save(EmpleadoDescanso.builder()
                .empleado(empleado)
                .fecha(fecha)
                .observaciones(observaciones)
                .build());
        return toMap(d);
    }

    @Transactional
    public void eliminar(Integer iddescanso, String username) {
        EmpleadoDescanso d = descansoRepository.findById(iddescanso)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Descanso no encontrado: " + iddescanso));
        validarAcceso(usuarioPorUsername(username), d.getEmpleado());
        descansoRepository.delete(d);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> porEmpleado(Integer idempleado, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now().minusMonths(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now().plusMonths(1);
        return descansoRepository.findByEmpleado_IdempleadoAndFechaBetweenOrderByFechaDesc(idempleado, d, h).stream()
                .map(this::toMap).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> porTienda(Integer codti, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now().minusMonths(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now().plusMonths(1);
        return descansoRepository.findByEmpleado_Usuario_Tienda_CodtiAndFechaBetweenOrderByFechaDesc(codti, d, h).stream()
                .map(this::toMap).collect(Collectors.toList());
    }

    // ── Categoría y saldo ────────────────────────────────────────────────────

    /** ROOT/ADMIN = administrativo; encargado de Zócalo/Superche/Corpo = categoría alta; el resto (otros
     *  encargados y apoyos: vendedores, técnicos) = categoría base. Automático por rol y sucursal. */
    public static String categoriaDe(Usuario u) {
        if (u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN) return CAT_ADMINISTRATIVO;
        if (u.getRol() == Rol.ENCARGADO_TIENDA && u.getTienda() != null && TIENDAS_ENCARGADO_TOP.contains(u.getTienda().getCodti()))
            return CAT_ENCARGADO_TOP;
        return CAT_ENCARGADO_APOYO;
    }

    public static int maximoDe(String categoria) {
        return MAXIMO_POR_CATEGORIA.getOrDefault(categoria, 2);
    }

    /** Días ganados automáticamente entre la fecha de corte y hoy: 1 por semana para todos, +1 por quincena
     *  además para administrativos y encargados de categoría alta. Package-visible para probarla sin Spring. */
    static int diasGanados(String categoria, LocalDate fechaCorte, LocalDate hoy) {
        if (fechaCorte == null || hoy.isBefore(fechaCorte)) return 0;
        long dias = ChronoUnit.DAYS.between(fechaCorte, hoy);
        int total = (int) (dias / 7);
        if (!CAT_ENCARGADO_APOYO.equals(categoria)) total += (int) (dias / 15);
        return total;
    }

    /** Tabla de vacaciones dignas (LFT Art. 76 reformado 2023): 12 días el año 1, +2 por cada año hasta el 5
     *  (20 el año 5), y +2 cada 5 años después de eso (22 en el 6-10, 24 en el 11-15, ...). Package-visible
     *  para probarla sin Spring. */
    static int diasVacacionesLey(int aniosServicio) {
        if (aniosServicio <= 0) return 0;
        if (aniosServicio <= 5) return 10 + aniosServicio * 2;
        return 22 + ((aniosServicio - 6) / 5) * 2;
    }

    /** Años completos de servicio cumplidos a partir de la fecha de ingreso (0 si aún no cumple el primero). */
    static int aniosServicio(LocalDate fechaIngreso, LocalDate hoy) {
        if (fechaIngreso == null || hoy.isBefore(fechaIngreso)) return 0;
        return Period.between(fechaIngreso, hoy).getYears();
    }

    /** Si el empleado acaba de cumplir un aniversario cuyas vacaciones de ley todavía no se le registraron
     *  como ajuste, arma la sugerencia (año, días, motivo) para que un admin la confirme con un clic — nunca
     *  se aplica sola, el negocio tiene demasiadas excepciones (bajas, licencias, adelantos) para automatizarla
     *  del todo. Solo detecta el aniversario más reciente; uno anterior sin reclamar se agrega a mano. */
    static Map<String, Object> sugerenciaVacaciones(LocalDate fechaIngreso, LocalDate hoy, List<EmpleadoDescansoAjuste> ajustes) {
        int anios = aniosServicio(fechaIngreso, hoy);
        if (anios < 1) return null;
        String motivo = motivoVacacionesLey(anios);
        boolean yaRegistrado = ajustes.stream().anyMatch(a -> motivo.equals(a.getMotivo()));
        if (yaRegistrado) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("anios", anios);
        m.put("dias", diasVacacionesLey(anios));
        m.put("motivo", motivo);
        return m;
    }

    private static String motivoVacacionesLey(int anios) {
        return "Vacaciones de ley (aniversario " + anios + ")";
    }

    @Transactional
    public Map<String, Object> establecerSaldoInicial(Integer idempleado, Integer saldoInicial, LocalDate fechaCorte, String username) {
        EmpleadoPerfil empleado = empleadoDe(idempleado);
        exigirAdmin(usuarioPorUsername(username), "fijar el saldo inicial de descansos");
        if (saldoInicial == null || saldoInicial < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El saldo inicial no puede ser negativo.");
        if (fechaCorte == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la fecha de corte.");

        EmpleadoDescansoSaldo s = saldoRepository.findById(idempleado)
                .orElseGet(() -> EmpleadoDescansoSaldo.builder().empleado(empleado).build());
        s.setSaldoInicial(saldoInicial);
        s.setFechaCorte(fechaCorte);
        saldoRepository.save(s);
        return calcularSaldo(empleado);
    }

    @Transactional
    public Map<String, Object> registrarAjuste(Integer idempleado, Integer dias, String motivo, String username) {
        EmpleadoPerfil empleado = empleadoDe(idempleado);
        Usuario quien = usuarioPorUsername(username);
        exigirAdmin(quien, "ajustar el saldo de descansos");
        if (dias == null || dias == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique cuántos días sumar o restar (distinto de cero).");
        if (motivo == null || motivo.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo del ajuste (vacaciones, salud, corrección...).");

        ajusteRepository.save(EmpleadoDescansoAjuste.builder()
                .empleado(empleado).fecha(LocalDate.now()).dias(dias).motivo(motivo.trim()).registradoPor(quien).build());
        return calcularSaldo(empleado);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> saldoDe(Integer idempleado, String username) {
        EmpleadoPerfil empleado = empleadoDe(idempleado);
        validarAcceso(usuarioPorUsername(username), empleado);
        return calcularSaldo(empleado);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ajustesDe(Integer idempleado, String username) {
        EmpleadoPerfil empleado = empleadoDe(idempleado);
        validarAcceso(usuarioPorUsername(username), empleado);
        return ajusteRepository.findByEmpleado_IdempleadoOrderByFechaDesc(idempleado).stream()
                .map(this::toMapAjuste).collect(Collectors.toList());
    }

    /** Reporte de saldo de cada empleado activo. Con {@code codti}, solo esa sucursal (un encargado, solo la
     *  suya); sin él, todas las sucursales (solo ROOT/ADMIN). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> reporte(Integer codti, String username) {
        Usuario quien = usuarioPorUsername(username);
        boolean admin = quien.getRol() == Rol.ROOT || quien.getRol() == Rol.ADMIN;
        List<EmpleadoPerfil> empleados;
        if (codti != null) {
            if (!admin && (quien.getTienda() == null || !quien.getTienda().getCodti().equals(codti)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes ver el reporte de tu sucursal.");
            empleados = empleadoPerfilRepository.findByActivoTrueAndUsuario_Tienda_Codti(codti);
        } else {
            if (!admin)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador ve el reporte de todas las sucursales.");
            empleados = empleadoPerfilRepository.findByActivoTrue();
        }
        return empleados.stream().map(this::calcularSaldo).collect(Collectors.toList());
    }

    private Map<String, Object> calcularSaldo(EmpleadoPerfil empleado) {
        Usuario u = empleado.getUsuario();
        String categoria = categoriaDe(u);
        int maximo = maximoDe(categoria);

        EmpleadoDescansoSaldo ancla = saldoRepository.findById(empleado.getIdempleado()).orElse(null);
        int saldoInicial = ancla != null ? ancla.getSaldoInicial() : 0;
        LocalDate fechaCorte = ancla != null ? ancla.getFechaCorte()
                : (empleado.getFechaIngreso() != null ? empleado.getFechaIngreso() : LocalDate.now());

        int ganados = diasGanados(categoria, fechaCorte, LocalDate.now());
        long tomados = descansoRepository.countByEmpleado_IdempleadoAndFechaGreaterThanEqual(empleado.getIdempleado(), fechaCorte);
        List<EmpleadoDescansoAjuste> listaAjustes = ajusteRepository.findByEmpleado_Idempleado(empleado.getIdempleado());
        int ajustes = listaAjustes.stream().mapToInt(EmpleadoDescansoAjuste::getDias).sum();
        int saldoActual = saldoInicial + ganados - (int) tomados + ajustes;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idempleado", empleado.getIdempleado());
        m.put("nombreEmpleado", u.getNombreCompleto());
        m.put("nombreTienda", u.getTienda() != null ? u.getTienda().getNombre() : null);
        m.put("rol", u.getRol());
        m.put("categoria", categoria);
        m.put("categoriaDisplay", categoriaDisplay(categoria));
        m.put("configurado", ancla != null);
        m.put("saldoInicial", saldoInicial);
        m.put("fechaCorte", fechaCorte);
        m.put("diasGanados", ganados);
        m.put("diasTomados", (int) tomados);
        m.put("ajustesTotal", ajustes);
        m.put("saldoActual", saldoActual);
        m.put("maximo", maximo);
        m.put("excedeMaximo", saldoActual > maximo);
        m.put("sugerenciaVacaciones", sugerenciaVacaciones(empleado.getFechaIngreso(), LocalDate.now(), listaAjustes));
        return m;
    }

    private String categoriaDisplay(String c) {
        return switch (c) {
            case CAT_ADMINISTRATIVO -> "Administrativo";
            case CAT_ENCARGADO_TOP -> "Encargado (Zócalo/Superche/Corpo)";
            default -> "Encargado/apoyo";
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private EmpleadoPerfil empleadoDe(Integer idempleado) {
        return empleadoPerfilRepository.findById(idempleado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empleado no encontrado: " + idempleado));
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private void exigirAdmin(Usuario u, String accion) {
        if (u.getRol() != Rol.ROOT && u.getRol() != Rol.ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede " + accion + ".");
    }

    /** ROOT/ADMIN operan cualquier empleado; un encargado solo los de su propia sucursal. */
    private void validarAcceso(Usuario usuario, EmpleadoPerfil empleado) {
        if (usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN) return;
        Integer tiendaEmpleado = empleado.getUsuario().getTienda() != null ? empleado.getUsuario().getTienda().getCodti() : null;
        if (usuario.getTienda() == null || tiendaEmpleado == null || !usuario.getTienda().getCodti().equals(tiendaEmpleado))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes gestionar descansos del personal de tu sucursal.");
    }

    private Map<String, Object> toMap(EmpleadoDescanso d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("iddescanso", d.getIddescanso());
        m.put("idempleado", d.getEmpleado().getIdempleado());
        m.put("nombreEmpleado", d.getEmpleado().getUsuario().getNombreCompleto());
        m.put("fecha", d.getFecha());
        m.put("observaciones", d.getObservaciones());
        return m;
    }

    private Map<String, Object> toMapAjuste(EmpleadoDescansoAjuste a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idajuste", a.getIdajuste());
        m.put("fecha", a.getFecha());
        m.put("dias", a.getDias());
        m.put("motivo", a.getMotivo());
        m.put("registradoPor", a.getRegistradoPor() != null ? a.getRegistradoPor().getNombreCompleto() : null);
        return m;
    }
}
