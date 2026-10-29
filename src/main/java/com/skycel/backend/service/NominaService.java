package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.EmpleadoPrestamo;
import com.skycel.backend.domain.entity.NominaDeduccion;
import com.skycel.backend.domain.entity.NominaDetalle;
import com.skycel.backend.domain.entity.NominaPercepcion;
import com.skycel.backend.domain.entity.NominaPeriodo;
import com.skycel.backend.domain.entity.SesionCaja;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.CajaRepository;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.NominaDeduccionRepository;
import com.skycel.backend.repository.NominaDetalleRepository;
import com.skycel.backend.repository.NominaPercepcionRepository;
import com.skycel.backend.repository.NominaPeriodoRepository;
import com.skycel.backend.repository.SesionCajaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nómina quincenal: al abrir un período se genera una línea (NominaDetalle) por cada empleado activo, con
 * su sueldo base como primera percepción y, si tiene un préstamo activo, una deducción sugerida por el saldo
 * pendiente. Percepciones y deducciones se pueden agregar/editar libremente mientras la línea siga pendiente.
 * Los faltantes de caja (corte con menos efectivo del esperado) se descuentan solos como deducción de quien cerró el turno.
 * Solo ROOT/ADMIN: es dinero de nómina, no se delega a encargados de tienda.
 */
@Service
@RequiredArgsConstructor
public class NominaService {

    public static final byte PERIODO_ABIERTO = 0;
    public static final byte PERIODO_CERRADO = 1;

    public static final byte DETALLE_PENDIENTE = 0;
    public static final byte DETALLE_PAGADO = 1;

    private static final String CONCEPTO_SUELDO_BASE = "Sueldo base";
    private static final String CONCEPTO_ABONO_PRESTAMO = "Abono a préstamo";

    /** Lo que pasó con el faltante de un corte (ver {@link SesionCaja#getFaltanteNomina()}). */
    public static final byte FALTANTE_DESCONTADO = 1;
    public static final byte FALTANTE_PERDONADO = 2;

    private final NominaPeriodoRepository periodoRepository;
    private final NominaDetalleRepository detalleRepository;
    private final NominaPercepcionRepository percepcionRepository;
    private final NominaDeduccionRepository deduccionRepository;
    private final EmpleadoPerfilRepository empleadoPerfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final SesionCajaRepository sesionRepository;
    private final CajaRepository cajaRepository;
    private final EmpleadoPrestamoService prestamoService;
    private final MovimientoCajaService movimientoCajaService;

    // ── Períodos ─────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> abrirPeriodo(LocalDate fechaInicio, LocalDate fechaFin, LocalDate fechaPago, String username) {
        if (fechaInicio == null || fechaFin == null || fechaPago == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique fecha de inicio, fin y pago del período.");
        if (fechaFin.isBefore(fechaInicio))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin no puede ser anterior a la de inicio.");
        periodoRepository.findFirstByEstadoOrderByFechaInicioDesc(PERIODO_ABIERTO)
                .ifPresent(p -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya hay un período de nómina abierto (#" + p.getIdperiodo() + "); ciérrelo antes de abrir otro."); });

        NominaPeriodo periodo = periodoRepository.save(NominaPeriodo.builder()
                .fechaInicio(fechaInicio).fechaFin(fechaFin).fechaPago(fechaPago)
                .estado(PERIODO_ABIERTO).build());

        for (EmpleadoPerfil empleado : empleadoPerfilRepository.findByActivoTrue()) {
            NominaDetalle detalle = detalleRepository.save(NominaDetalle.builder()
                    .periodo(periodo).empleado(empleado).estado(DETALLE_PENDIENTE)
                    .totalPercepciones(BigDecimal.ZERO).totalDeducciones(BigDecimal.ZERO).totalNeto(BigDecimal.ZERO)
                    .build());
            BigDecimal sueldo = empleado.getSueldoBase() != null ? empleado.getSueldoBase() : BigDecimal.ZERO;
            percepcionRepository.save(NominaPercepcion.builder().detalle(detalle).concepto(CONCEPTO_SUELDO_BASE).monto(sueldo).build());

            Optional<EmpleadoPrestamo> prestamo = prestamoService.activoDe(empleado.getIdempleado());
            if (prestamo.isPresent() && prestamo.get().getSaldoPendiente().signum() > 0) {
                BigDecimal abono = prestamo.get().getSaldoPendiente().min(sueldo);
                if (abono.signum() > 0) {
                    deduccionRepository.save(NominaDeduccion.builder().detalle(detalle).concepto(CONCEPTO_ABONO_PRESTAMO)
                            .monto(abono).prestamo(prestamo.get()).build());
                }
            }
            recalcularTotales(detalle);
        }
        descontarFaltantes(periodo);
        return toMapPeriodo(periodo);
    }

    /**
     * Pasa a deducciones los faltantes de caja que todavía no se descuentan: los de los cortes cerrados desde donde terminó el
     * período anterior (o desde el inicio de este, si es el primero) hasta el fin de este. Cada faltante se carga a quien cerró el
     * turno; si lo cerró un administrador, a quien lo abrió. Solo a las líneas que siguen pendientes de pago. Si el administrador
     * quita la deducción, el faltante queda perdonado y no se vuelve a cargar.
     *
     * @return cuántos faltantes se descontaron
     */
    @Transactional
    public int descontarFaltantes(NominaPeriodo periodo) {
        if (periodo.getFechaInicio() == null || periodo.getFechaFin() == null) return 0;
        LocalDate desde = periodoRepository.findAllByOrderByFechaInicioDesc().stream()
                .filter(p -> !p.getIdperiodo().equals(periodo.getIdperiodo()) && p.getFechaInicio().isBefore(periodo.getFechaInicio()))
                .map(p -> p.getFechaFin().plusDays(1)).findFirst()
                .map(f -> f.isBefore(periodo.getFechaInicio()) ? f : periodo.getFechaInicio())
                .orElse(periodo.getFechaInicio());
        List<SesionCaja> faltantes = sesionRepository.faltantesPorDescontar(desde.atStartOfDay(), periodo.getFechaFin().plusDays(1).atStartOfDay());
        if (faltantes == null || faltantes.isEmpty()) return 0;

        Map<Integer, NominaDetalle> porUsuario = new HashMap<>();
        for (NominaDetalle d : detalleRepository.findByPeriodo_IdperiodoOrderByIddetalleAsc(periodo.getIdperiodo()))
            if (d.getEstado() == DETALLE_PENDIENTE && d.getEmpleado().getUsuario() != null)
                porUsuario.put(d.getEmpleado().getUsuario().getIdusuario(), d);

        Set<NominaDetalle> tocados = new LinkedHashSet<>();
        int descontados = 0;
        for (SesionCaja s : faltantes) {
            Usuario responsable = responsableDelFaltante(s);
            NominaDetalle detalle = responsable == null ? null : porUsuario.get(responsable.getIdusuario());
            if (detalle == null) continue;
            deduccionRepository.save(NominaDeduccion.builder().detalle(detalle).concepto(conceptoFaltante(s))
                    .monto(s.getDiferencia().negate()).sesion(s).build());
            s.setFaltanteNomina(FALTANTE_DESCONTADO);
            sesionRepository.save(s);
            tocados.add(detalle);
            descontados++;
        }
        tocados.forEach(this::recalcularTotales);
        return descontados;
    }

    /** Trae a un período abierto los faltantes de caja que se hayan registrado desde que se abrió. */
    @Transactional
    public Map<String, Object> actualizarFaltantes(Integer idperiodo) {
        NominaPeriodo periodo = periodoDe(idperiodo);
        if (periodo.getEstado() != PERIODO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este período ya está cerrado.");
        return Map.of("descontados", descontarFaltantes(periodo));
    }

    /** A quién se le carga el faltante de un corte: a quien lo cerró; si lo cerró un administrador, a quien abrió el turno. */
    static Usuario responsableDelFaltante(SesionCaja s) {
        Usuario cierre = s.getUsuarioCierre(), apertura = s.getUsuarioApertura();
        if (cierre == null) return apertura;
        if (esDirectivo(cierre) && apertura != null && !esDirectivo(apertura)) return apertura;
        return cierre;
    }

    private static boolean esDirectivo(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    private static String conceptoFaltante(SesionCaja s) {
        String fecha = s.getFechaCierre() != null ? s.getFechaCierre().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
        String caja = s.getCaja() != null && s.getCaja().getNombreCaja() != null ? " · " + s.getCaja().getNombreCaja() : "";
        String concepto = "Faltante de caja del " + fecha + caja;
        return concepto.length() > 100 ? concepto.substring(0, 100) : concepto;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> periodoActivo() {
        return periodoRepository.findFirstByEstadoOrderByFechaInicioDesc(PERIODO_ABIERTO)
                .map(this::toMapPeriodo)
                .orElseGet(() -> { Map<String, Object> m = new LinkedHashMap<>(); m.put("activo", false); return m; });
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> periodos() {
        return periodoRepository.findAllByOrderByFechaInicioDesc().stream().map(this::toMapPeriodo).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> cerrarPeriodo(Integer idperiodo) {
        NominaPeriodo periodo = periodoDe(idperiodo);
        if (periodo.getEstado() != PERIODO_ABIERTO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este período ya está cerrado.");
        long pendientes = detalleRepository.countByPeriodo_IdperiodoAndEstado(idperiodo, DETALLE_PENDIENTE);
        if (pendientes > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aún hay " + pendientes + " línea(s) sin pagar en este período.");
        periodo.setEstado(PERIODO_CERRADO);
        return toMapPeriodo(periodoRepository.save(periodo));
    }

    // ── Líneas por empleado ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> porPeriodo(Integer idperiodo) {
        periodoDe(idperiodo);
        return Map.of("detalles", detalleRepository.findByPeriodo_IdperiodoOrderByIddetalleAsc(idperiodo).stream()
                .map(this::toMapDetalle).collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(Integer iddetalle) {
        NominaDetalle detalle = detalleDe(iddetalle);
        Map<String, Object> m = new LinkedHashMap<>(toMapDetalle(detalle));
        m.put("percepciones", percepcionRepository.findByDetalle_IddetalleOrderByIdpercepcionAsc(iddetalle).stream()
                .map(this::toMapPercepcion).collect(Collectors.toList()));
        m.put("deducciones", deduccionRepository.findByDetalle_IddetalleOrderByIddeduccionAsc(iddetalle).stream()
                .map(this::toMapDeduccion).collect(Collectors.toList()));
        return m;
    }

    @Transactional
    public Map<String, Object> agregarPercepcion(Integer iddetalle, String concepto, BigDecimal monto) {
        NominaDetalle detalle = detalleEditable(iddetalle);
        validarConceptoMonto(concepto, monto);
        percepcionRepository.save(NominaPercepcion.builder().detalle(detalle).concepto(concepto.trim()).monto(monto).build());
        recalcularTotales(detalle);
        return detalle(iddetalle);
    }

    @Transactional
    public Map<String, Object> eliminarPercepcion(Integer idpercepcion) {
        NominaPercepcion p = percepcionRepository.findById(idpercepcion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Percepción no encontrada: " + idpercepcion));
        NominaDetalle detalle = detalleEditable(p.getDetalle().getIddetalle());
        percepcionRepository.delete(p);
        recalcularTotales(detalle);
        return detalle(detalle.getIddetalle());
    }

    @Transactional
    public Map<String, Object> agregarDeduccion(Integer iddetalle, String concepto, BigDecimal monto) {
        NominaDetalle detalle = detalleEditable(iddetalle);
        validarConceptoMonto(concepto, monto);
        deduccionRepository.save(NominaDeduccion.builder().detalle(detalle).concepto(concepto.trim()).monto(monto).build());
        recalcularTotales(detalle);
        return detalle(iddetalle);
    }

    @Transactional
    public Map<String, Object> eliminarDeduccion(Integer iddeduccion) {
        NominaDeduccion d = deduccionRepository.findById(iddeduccion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deducción no encontrada: " + iddeduccion));
        NominaDetalle detalle = detalleEditable(d.getDetalle().getIddetalle());
        if (d.getSesion() != null) {                    // quitar un faltante de caja es perdonarlo: no se vuelve a cargar
            d.getSesion().setFaltanteNomina(FALTANTE_PERDONADO);
            sesionRepository.save(d.getSesion());
        }
        deduccionRepository.delete(d);
        recalcularTotales(detalle);
        return detalle(detalle.getIddetalle());
    }

    @Transactional
    public Map<String, Object> pagar(Integer iddetalle, Integer idCaja, String username) {
        NominaDetalle detalle = detalleEditable(iddetalle);
        descontarFaltantes(detalle.getPeriodo());       // los faltantes de caja que hayan llegado desde que se abrió el período
        if (detalle.getTotalNeto().signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El neto de esta línea es negativo ($" + detalle.getTotalNeto() + "); ajuste percepciones o deducciones antes de pagar.");
        if (idCaja == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique de qué caja sale el pago.");

        Usuario usuario = usuarioPorUsername(username);
        movimientoCajaService.registrarPagoNomina(idCaja, detalle, usuario);

        for (NominaDeduccion d : deduccionRepository.findByDetalle_IddetalleOrderByIddeduccionAsc(iddetalle)) {
            if (d.getPrestamo() != null) prestamoService.abonar(d.getPrestamo().getIdprestamo(), d.getMonto());
        }

        detalle.setEstado(DETALLE_PAGADO);
        detalle.setFechaPago(java.time.LocalDateTime.now());
        detalle.setCaja(cajaRepository.findById(idCaja)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caja no encontrada: " + idCaja)));
        detalleRepository.save(detalle);
        return detalle(iddetalle);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void recalcularTotales(NominaDetalle detalle) {
        BigDecimal percepciones = percepcionRepository.findByDetalle_IddetalleOrderByIdpercepcionAsc(detalle.getIddetalle())
                .stream().map(NominaPercepcion::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deducciones = deduccionRepository.findByDetalle_IddetalleOrderByIddeduccionAsc(detalle.getIddetalle())
                .stream().map(NominaDeduccion::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        detalle.setTotalPercepciones(percepciones);
        detalle.setTotalDeducciones(deducciones);
        detalle.setTotalNeto(percepciones.subtract(deducciones));
        detalleRepository.save(detalle);
    }

    private void validarConceptoMonto(String concepto, BigDecimal monto) {
        if (concepto == null || concepto.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el concepto.");
        if (monto == null || monto.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a cero.");
    }

    private NominaDetalle detalleEditable(Integer iddetalle) {
        NominaDetalle detalle = detalleDe(iddetalle);
        if (detalle.getEstado() != DETALLE_PENDIENTE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta línea de nómina ya está pagada.");
        return detalle;
    }

    private NominaDetalle detalleDe(Integer iddetalle) {
        return detalleRepository.findById(iddetalle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Línea de nómina no encontrada: " + iddetalle));
    }

    private NominaPeriodo periodoDe(Integer idperiodo) {
        return periodoRepository.findById(idperiodo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Período de nómina no encontrado: " + idperiodo));
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private Map<String, Object> toMapPeriodo(NominaPeriodo p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activo", true);
        m.put("idperiodo", p.getIdperiodo());
        m.put("fechaInicio", p.getFechaInicio());
        m.put("fechaFin", p.getFechaFin());
        m.put("fechaPago", p.getFechaPago());
        m.put("estado", p.getEstado());
        m.put("estadoDisplay", p.getEstado() == PERIODO_ABIERTO ? "Abierto" : "Cerrado");
        List<NominaDetalle> detalles = detalleRepository.findByPeriodo_IdperiodoOrderByIddetalleAsc(p.getIdperiodo());
        m.put("totalEmpleados", detalles.size());
        m.put("totalPendientes", detalles.stream().filter(d -> d.getEstado() == DETALLE_PENDIENTE).count());
        m.put("totalNeto", detalles.stream().map(NominaDetalle::getTotalNeto).reduce(BigDecimal.ZERO, BigDecimal::add));
        return m;
    }

    private Map<String, Object> toMapDetalle(NominaDetalle d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("iddetalle", d.getIddetalle());
        m.put("idperiodo", d.getPeriodo().getIdperiodo());
        m.put("idempleado", d.getEmpleado().getIdempleado());
        m.put("nombreEmpleado", d.getEmpleado().getUsuario().getNombreCompleto());
        m.put("nombreTienda", d.getEmpleado().getUsuario().getTienda() != null ? d.getEmpleado().getUsuario().getTienda().getNombre() : null);
        m.put("estado", d.getEstado());
        m.put("estadoDisplay", d.getEstado() == DETALLE_PENDIENTE ? "Pendiente" : "Pagado");
        m.put("totalPercepciones", d.getTotalPercepciones());
        m.put("totalDeducciones", d.getTotalDeducciones());
        m.put("totalNeto", d.getTotalNeto());
        m.put("nombreCaja", d.getCaja() != null ? d.getCaja().getNombreCaja() : null);
        m.put("fechaPago", d.getFechaPago());
        return m;
    }

    private Map<String, Object> toMapPercepcion(NominaPercepcion p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idpercepcion", p.getIdpercepcion());
        m.put("concepto", p.getConcepto());
        m.put("monto", p.getMonto());
        return m;
    }

    private Map<String, Object> toMapDeduccion(NominaDeduccion d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("iddeduccion", d.getIddeduccion());
        m.put("concepto", d.getConcepto());
        m.put("monto", d.getMonto());
        m.put("idprestamo", d.getPrestamo() != null ? d.getPrestamo().getIdprestamo() : null);
        m.put("idsesionCaja", d.getSesion() != null ? d.getSesion().getIdsesionCaja() : null);
        return m;
    }
}
