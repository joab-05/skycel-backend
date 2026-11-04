package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ComisionTallerDescuento;
import com.skycel.backend.domain.entity.ComisionTallerTecnico;
import com.skycel.backend.domain.entity.Gasto;
import com.skycel.backend.domain.entity.NominaDetalle;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.finanzastaller.ComisionTecnicoRequestDto;
import com.skycel.backend.dto.finanzastaller.DescuentoComisionRequestDto;
import com.skycel.backend.dto.finanzastaller.GastoRequestDto;
import com.skycel.backend.repository.ComisionTallerDescuentoRepository;
import com.skycel.backend.repository.ComisionTallerTecnicoRepository;
import com.skycel.backend.repository.GastoRepository;
import com.skycel.backend.repository.NominaDetalleRepository;
import com.skycel.backend.repository.OrdenServicioRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Las finanzas mensuales del taller (lo que hoy se lleva en la libreta): lo cobrado en reparaciones entregadas, lo que
 * costaron las piezas, los gastos del negocio, la ganancia neta y la comisión de cada técnico (un porcentaje de la
 * ganancia neta menos lo que se le descuente). Todo se calcula en vivo con lo ya capturado: solo los gastos y los
 * descuentos se capturan aparte. Solo la administración.
 */
@Service
@RequiredArgsConstructor
public class FinanzasTallerService {

    /** Las categorías de gasto: código → nombre. */
    public static final Map<String, String> CATEGORIAS = new LinkedHashMap<>();
    static {
        CATEGORIAS.put("SUELDOS", "Sueldos (pagados fuera del sistema)");
        CATEGORIAS.put("RENTA", "Renta del local");
        CATEGORIAS.put("GASOLINA", "Gasolina");
        CATEGORIAS.put("HERRAMIENTA", "Herramienta e insumos");
        CATEGORIAS.put("SERVICIOS", "Luz, agua e internet");
        CATEGORIAS.put("SOFTWARE", "Software, schematics y borneo");
        CATEGORIAS.put("PUBLICIDAD", "Publicidad");
        CATEGORIAS.put("OTRO", "Otro");
    }
    /** Los gastos fijos son los sueldos; todo lo demás es operativo (como en la libreta). */
    private static final Set<String> FIJAS = Set.of("SUELDOS");

    private static final byte NOMINA_PAGADA = 1;

    private final OrdenServicioRepository ordenRepository;
    private final GastoRepository gastoRepository;
    private final NominaDetalleRepository nominaDetalleRepository;
    private final ComisionTallerTecnicoRepository comisionRepository;
    private final ComisionTallerDescuentoRepository descuentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TiendaRepository tiendaRepository;

    // ── Reporte del mes ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> mensual(int anio, int mes, String username) {
        exigirAdministracion(username);
        YearMonth ym = periodo(anio, mes);
        LocalDate primero = ym.atDay(1);
        LocalDate ultimo = ym.atEndOfMonth();

        Ventas v = ventas(primero, ultimo);

        // gastos del mes: los de una sola vez y los recurrentes que siguen vigentes
        List<Map<String, Object>> gastos = new ArrayList<>();
        BigDecimal fijosManual = BigDecimal.ZERO, operativos = BigDecimal.ZERO;
        for (Gasto g : gastosDelMes(ym)) {
            gastos.add(gastoMapa(g));
            if (FIJAS.contains(g.getCategoria())) fijosManual = fijosManual.add(g.getMonto());
            else operativos = operativos.add(g.getMonto());
        }

        // la nómina de los técnicos que ya se pagó desde el sistema (no se vuelve a capturar como gasto); la del personal de
        // las tiendas no es del taller y no entra en este reporte
        List<Map<String, Object>> nomina = new ArrayList<>();
        BigDecimal nominaSistema = BigDecimal.ZERO;
        for (NominaDetalle d : nominaDetalleRepository.findByEstadoAndFechaPagoBetweenOrderByFechaPagoAsc(
                NOMINA_PAGADA, primero.atStartOfDay(), ultimo.atTime(LocalTime.MAX))) {
            if (d.getEmpleado() == null || d.getEmpleado().getUsuario() == null || d.getEmpleado().getUsuario().getRol() != Rol.TECNICO) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("empleado", d.getEmpleado() != null && d.getEmpleado().getUsuario() != null ? d.getEmpleado().getUsuario().getNombreCompleto() : "—");
            m.put("monto", d.getTotalNeto());
            nomina.add(m);
            nominaSistema = nominaSistema.add(d.getTotalNeto());
        }

        BigDecimal fijos = fijosManual.add(nominaSistema);
        BigDecimal bruta = v.ventas.subtract(v.costo);
        BigDecimal neta = bruta.subtract(fijos).subtract(operativos);

        // el mes anterior, para comparar
        YearMonth ant = ym.minusMonths(1);
        Ventas va = ventas(ant.atDay(1), ant.atEndOfMonth());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("anio", anio);
        r.put("mes", mes);
        r.put("ordenes", v.ordenes.size());
        r.put("ventas", v.ventas);
        r.put("inversion", v.costo);
        r.put("gananciaBruta", bruta);
        r.put("gastosFijos", fijos);
        r.put("nominaSistema", nominaSistema);
        r.put("nomina", nomina);
        r.put("gastosOperativos", operativos);
        r.put("gananciaNeta", neta);
        r.put("anterior", Map.of("ordenes", va.ordenes.size(), "ventas", va.ventas, "gananciaBruta", va.ventas.subtract(va.costo)));
        r.put("porTienda", porTienda(v));
        r.put("gastos", gastos);
        r.put("tecnicos", tecnicos(v, neta, anio, mes));
        r.put("categorias", categoriasLista());
        return r;
    }

    /** Lo cobrado y lo que costó, por orden entregada, técnico y sucursal. */
    private Ventas ventas(LocalDate primero, LocalDate ultimo) {
        Ventas v = new Ventas();
        for (Object[] f : ordenRepository.lineasEntregadas(primero.atStartOfDay(), ultimo.atTime(LocalTime.MAX))) {
            Integer idTec = (Integer) f[0];
            String tec = f[1] != null ? (String) f[1] : "Sin técnico";
            Integer codti = (Integer) f[2];
            String tienda = (String) f[3];
            Integer idorden = (Integer) f[4];
            BigDecimal cant = BigDecimal.valueOf(((Number) f[6]).longValue());
            BigDecimal precio = ((BigDecimal) f[5]).multiply(cant);
            BigDecimal costo = ((BigDecimal) f[7]).multiply(cant);

            v.ordenes.add(idorden);
            v.ventas = v.ventas.add(precio);
            v.costo = v.costo.add(costo);

            Grupo gt = v.porTecnico.computeIfAbsent(idTec == null ? -1 : idTec, k -> new Grupo(idTec, tec));
            gt.ordenes.add(idorden); gt.ventas = gt.ventas.add(precio); gt.costo = gt.costo.add(costo);
            Grupo gs = v.porTienda.computeIfAbsent(codti, k -> new Grupo(codti, tienda));
            gs.ordenes.add(idorden); gs.ventas = gs.ventas.add(precio); gs.costo = gs.costo.add(costo);
        }
        return v;
    }

    private List<Map<String, Object>> porTienda(Ventas v) {
        List<Map<String, Object>> lista = new ArrayList<>();
        v.porTienda.values().stream().sorted((a, b) -> b.ventas.compareTo(a.ventas)).forEach(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codti", g.id);
            m.put("tienda", g.nombre);
            m.put("ordenes", g.ordenes.size());
            m.put("ventas", g.ventas);
            m.put("inversion", g.costo);
            m.put("ganancia", g.ventas.subtract(g.costo));
            lista.add(m);
        });
        return lista;
    }

    /** Cada técnico: lo que generó, su porcentaje, su comisión, lo que se le descuenta y lo que se le paga. */
    private List<Map<String, Object>> tecnicos(Ventas v, BigDecimal neta, int anio, int mes) {
        Map<Integer, Usuario> usuarios = new LinkedHashMap<>();
        for (Usuario u : usuarioRepository.findByRolAndActivoTrue(Rol.TECNICO)) usuarios.put(u.getIdusuario(), u);
        Map<Integer, BigDecimal> porcentajes = new HashMap<>();
        comisionRepository.findAll().forEach(c -> porcentajes.put(c.getIdusuario(), c.getPorcentaje()));
        List<ComisionTallerDescuento> descuentos = descuentoRepository.findByAnioAndMesOrderByIddescuentoAsc(anio, mes);

        // los técnicos que cuentan: los activos, los que generaron algo y los que tienen descuentos
        Set<Integer> ids = new LinkedHashSet<>(usuarios.keySet());
        v.porTecnico.values().stream().filter(g -> g.id != null).forEach(g -> ids.add(g.id));
        descuentos.forEach(d -> ids.add(d.getIdusuario()));

        List<Map<String, Object>> lista = new ArrayList<>();
        for (Integer id : ids) {
            Usuario u = usuarios.get(id);
            if (u == null) u = usuarioRepository.findById(id).orElse(null);
            Grupo g = v.porTecnico.get(id);
            BigDecimal generado = g == null ? BigDecimal.ZERO : g.ventas.subtract(g.costo);
            BigDecimal pct = porcentajes.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal comision = neta.signum() > 0 ? neta.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            List<Map<String, Object>> desc = new ArrayList<>();
            BigDecimal totalDesc = BigDecimal.ZERO;
            for (ComisionTallerDescuento d : descuentos) {
                if (!d.getIdusuario().equals(id)) continue;
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("iddescuento", d.getIddescuento());
                dm.put("concepto", d.getConcepto());
                dm.put("monto", d.getMonto());
                desc.add(dm);
                totalDesc = totalDesc.add(d.getMonto());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idusuario", id);
            m.put("tecnico", u != null ? u.getNombreCompleto() : "Técnico " + id);
            m.put("activo", u != null && Boolean.TRUE.equals(u.getActivo()));
            m.put("ordenes", g == null ? 0 : g.ordenes.size());
            m.put("ventas", g == null ? BigDecimal.ZERO : g.ventas);
            m.put("generado", generado);
            m.put("porcentaje", pct);
            m.put("comision", comision);
            m.put("descuentos", desc);
            m.put("totalDescuentos", totalDesc);
            m.put("aPagar", comision.subtract(totalDesc));
            lista.add(m);
        }
        return lista;
    }

    // ── Gastos ───────────────────────────────────────────────────────────────

    /** Los gastos que cuentan en un mes: los de una sola vez de ese mes y los recurrentes vigentes. */
    private List<Gasto> gastosDelMes(YearMonth ym) {
        List<Gasto> todos = new ArrayList<>(gastoRepository
                .findByActivoTrueAndRecurrenteFalseAndFechaBetweenOrderByFechaAscIdgastoAsc(ym.atDay(1), ym.atEndOfMonth()));
        for (Gasto g : gastoRepository.findByActivoTrueAndRecurrenteTrueAndFechaLessThanEqualOrderByFechaAscIdgastoAsc(ym.atEndOfMonth())) {
            if (g.getHastaFecha() == null || !YearMonth.from(g.getHastaFecha()).isBefore(ym)) todos.add(g);
        }
        return todos;
    }

    private Map<String, Object> gastoMapa(Gasto g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idgasto", g.getIdgasto());
        m.put("fecha", g.getFecha());
        m.put("concepto", g.getConcepto());
        m.put("categoria", g.getCategoria());
        m.put("categoriaDisplay", CATEGORIAS.getOrDefault(g.getCategoria(), g.getCategoria()));
        m.put("grupo", FIJAS.contains(g.getCategoria()) ? "FIJO" : "OPERATIVO");
        m.put("monto", g.getMonto());
        m.put("codti", g.getTienda() != null ? g.getTienda().getCodti() : null);
        m.put("tienda", g.getTienda() != null ? g.getTienda().getNombre() : null);
        m.put("recurrente", Boolean.TRUE.equals(g.getRecurrente()));
        m.put("hastaFecha", g.getHastaFecha());
        return m;
    }

    private List<Map<String, String>> categoriasLista() {
        List<Map<String, String>> lista = new ArrayList<>();
        CATEGORIAS.forEach((k, v) -> lista.add(Map.of("codigo", k, "nombre", v)));
        return lista;
    }

    @Transactional
    public Map<String, Object> crearGasto(GastoRequestDto dto, String username) {
        exigirAdministracion(username);
        Gasto g = Gasto.builder().creadoPor(username).build();
        aplicar(g, dto);
        return gastoMapa(gastoRepository.save(g));
    }

    @Transactional
    public Map<String, Object> actualizarGasto(Integer id, GastoRequestDto dto, String username) {
        exigirAdministracion(username);
        Gasto g = gastoRepository.findById(id).filter(x -> Boolean.TRUE.equals(x.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese gasto."));
        aplicar(g, dto);
        return gastoMapa(gastoRepository.save(g));
    }

    /** El gasto deja de contar (queda guardado, marcado como no activo). */
    @Transactional
    public void eliminarGasto(Integer id, String username) {
        exigirAdministracion(username);
        Gasto g = gastoRepository.findById(id).filter(x -> Boolean.TRUE.equals(x.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese gasto."));
        g.setActivo(false);
        gastoRepository.save(g);
    }

    private void aplicar(Gasto g, GastoRequestDto dto) {
        if (!CATEGORIAS.containsKey(dto.getCategoria())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría del gasto no es válida.");
        }
        boolean recurrente = Boolean.TRUE.equals(dto.getRecurrente());
        if (recurrente && dto.getHastaFecha() != null && YearMonth.from(dto.getHastaFecha()).isBefore(YearMonth.from(dto.getFecha()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El último mes no puede ser anterior al primero.");
        }
        Tienda tienda = null;
        if (dto.getCodti() != null) {
            tienda = tiendaRepository.findById(dto.getCodti())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No existe la sucursal indicada."));
        }
        g.setFecha(dto.getFecha());
        g.setConcepto(dto.getConcepto().trim());
        g.setCategoria(dto.getCategoria());
        g.setMonto(dto.getMonto().setScale(2, RoundingMode.HALF_UP));
        g.setTienda(tienda);
        g.setRecurrente(recurrente);
        g.setHastaFecha(recurrente ? dto.getHastaFecha() : null);
    }

    // ── Comisiones ───────────────────────────────────────────────────────────

    @Transactional
    public void fijarPorcentaje(Integer idusuario, ComisionTecnicoRequestDto dto, String username) {
        exigirAdministracion(username);
        Usuario u = usuarioRepository.findById(idusuario)
                .filter(x -> x.getRol() == Rol.TECNICO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese usuario no es un técnico."));
        comisionRepository.save(ComisionTallerTecnico.builder().idusuario(u.getIdusuario())
                .porcentaje(dto.getPorcentaje().setScale(2, RoundingMode.HALF_UP)).actualizadoPor(username).build());
    }

    @Transactional
    public Map<String, Object> crearDescuento(DescuentoComisionRequestDto dto, String username) {
        exigirAdministracion(username);
        periodo(dto.getAnio(), dto.getMes());
        usuarioRepository.findById(dto.getIdusuario())
                .filter(x -> x.getRol() == Rol.TECNICO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese usuario no es un técnico."));
        ComisionTallerDescuento d = descuentoRepository.save(ComisionTallerDescuento.builder()
                .anio(dto.getAnio()).mes(dto.getMes()).idusuario(dto.getIdusuario())
                .concepto(dto.getConcepto().trim()).monto(dto.getMonto().setScale(2, RoundingMode.HALF_UP)).creadoPor(username).build());
        return Map.of("iddescuento", d.getIddescuento());
    }

    @Transactional
    public void eliminarDescuento(Integer id, String username) {
        exigirAdministracion(username);
        if (!descuentoRepository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese descuento.");
        descuentoRepository.deleteById(id);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private void exigirAdministracion(String username) {
        Usuario u = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado."));
        if (u.getRol() != Rol.ROOT && u.getRol() != Rol.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la administración ve las finanzas del taller.");
        }
    }

    private static YearMonth periodo(int anio, int mes) {
        if (mes < 1 || mes > 12 || anio < 2020 || anio > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mes o el año no son válidos.");
        }
        return YearMonth.of(anio, mes);
    }

    private static class Grupo {
        final Integer id; final String nombre;
        final Set<Integer> ordenes = new HashSet<>();
        BigDecimal ventas = BigDecimal.ZERO, costo = BigDecimal.ZERO;
        Grupo(Integer id, String nombre) { this.id = id; this.nombre = nombre; }
    }

    private static class Ventas {
        final Set<Integer> ordenes = new HashSet<>();
        BigDecimal ventas = BigDecimal.ZERO, costo = BigDecimal.ZERO;
        final Map<Integer, Grupo> porTecnico = new LinkedHashMap<>();
        final Map<Integer, Grupo> porTienda = new LinkedHashMap<>();
    }
}
