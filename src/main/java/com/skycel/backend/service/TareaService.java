package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tarea;
import com.skycel.backend.domain.entity.TareaRutina;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.EstadoTarea;
import com.skycel.backend.domain.enums.MomentoTarea;
import com.skycel.backend.domain.enums.Permiso;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.tarea.TareaDtos.*;
import com.skycel.backend.repository.TareaRepository;
import com.skycel.backend.repository.TareaRutinaRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rutinas y tareas del día. Las <b>rutinas</b> son las plantillas que arma el administrador (qué se hace en la apertura, durante el
 * día y al cierre, y a quién le toca); cada día el sistema crea de ellas las <b>tareas</b> de cada tienda, que el personal marca como
 * hechas con una nota opcional. Una tarea de rutina sin persona asignada es de todo su rol en la tienda: la hace quien llegue primero.
 *
 * <p>Además hay <b>tareas puntuales</b>: algo suelto, con fecha límite, que el administrador o el encargado le asignan a una persona (o a un
 * puesto, o a cualquiera de la tienda). Una actividad (de rutina o puntual) puede pedir <b>revisión</b>: al marcarla queda «por revisar» y otra
 * persona con permiso la aprueba, o la regresa con un comentario y vuelve a quedar pendiente.
 *
 * <p>Las tareas del día se generan al abrir «Mis tareas», y también al encender el servidor (que está apagado de madrugada), para que el
 * reporte de cumplimiento cuente también los días en que nadie abrió la pantalla.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TareaService {

    private static final int MAX_DIAS_REPORTE = 92;
    private static final int MAX_NOTA = 500;
    /** Cuántos días de anticipación se ve una tarea puntual en «Mis tareas». */
    private static final int DIAS_DE_ANTICIPACION = 2;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("H:mm");

    /** Una sola generación a la vez: dos personas abriendo la pantalla al mismo tiempo no deben duplicar las tareas del día. */
    private static final Object GENERACION = new Object();

    private static final Comparator<Tarea> ORDEN_TAREAS = Comparator
            .comparing((Tarea t) -> t.esPuntual() ? 0 : 1)
            .thenComparing(t -> t.esPuntual() ? t.getFecha() : LocalDate.MIN)
            .thenComparing(t -> t.esPuntual() && t.getHoraLimite() != null ? t.getHoraLimite() : LocalTime.MAX)
            .thenComparing(t -> t.getMomento().ordinal())
            .thenComparing(Tarea::getOrden)
            .thenComparing(t -> t.getIdtarea() == null ? 0 : t.getIdtarea());

    private final TareaRutinaRepository rutinaRepository;
    private final TareaRepository tareaRepository;
    private final TiendaRepository tiendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;
    private final TransactionTemplate transaccion;

    // ── Rutinas (las arma quien tiene el permiso TAREAS_RUTINAS) ─────────────────

    @Transactional(readOnly = true)
    public List<RutinaDto> rutinas(Integer codti) {
        return rutinaRepository.listar(codti).stream()
                .sorted(Comparator.comparing((TareaRutina r) -> r.getTienda() == null ? 0 : r.getTienda().getCodti())
                        .thenComparing(r -> r.getMomento().ordinal())
                        .thenComparing(TareaRutina::getOrden)
                        .thenComparing(TareaRutina::getIdrutina))
                .map(this::aDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public RutinaDto crearRutina(RutinaRequest req, String username) {
        TareaRutina r = TareaRutina.builder().creadoPor(username).activa(true).build();
        aplicar(r, req);
        if (req.orden() == null) r.setOrden(siguienteOrden(r));
        return aDto(rutinaRepository.save(r));
    }

    /** Lo que se cambia aplica desde hoy: las tareas de hoy aún sin hacer se vuelven a generar con el texto nuevo. */
    @Transactional
    public RutinaDto actualizarRutina(Integer idrutina, RutinaRequest req) {
        TareaRutina r = rutina(idrutina);
        aplicar(r, req);
        rutinaRepository.save(r);
        tareaRepository.borrarDesde(idrutina, LocalDate.now(), EstadoTarea.PENDIENTE);
        return aDto(r);
    }

    /** No se borra: se desactiva, para conservar el historial de lo que ya se hizo con ella. */
    @Transactional
    public void quitarRutina(Integer idrutina) {
        TareaRutina r = rutina(idrutina);
        r.setActiva(false);
        rutinaRepository.save(r);
        tareaRepository.borrarDesde(idrutina, LocalDate.now(), EstadoTarea.PENDIENTE);
    }

    /** Sube (−1) o baja (+1) una actividad dentro de su momento del día y su tienda. */
    @Transactional
    public List<RutinaDto> moverRutina(Integer idrutina, int direccion) {
        if (direccion != -1 && direccion != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La dirección es -1 (subir) o 1 (bajar).");
        TareaRutina r = rutina(idrutina);
        List<TareaRutina> grupo = rutinaRepository.listar(null).stream()
                .filter(x -> mismaTienda(x, r) && x.getMomento() == r.getMomento() && Boolean.TRUE.equals(x.getActiva()))
                .sorted(Comparator.comparing(TareaRutina::getOrden).thenComparing(TareaRutina::getIdrutina))
                .collect(Collectors.toCollection(ArrayList::new));
        int i = -1;
        for (int k = 0; k < grupo.size(); k++) if (grupo.get(k).getIdrutina().equals(idrutina)) i = k;
        int j = i + direccion;
        if (i >= 0 && j >= 0 && j < grupo.size()) {
            Collections.swap(grupo, i, j);
            int orden = 10;
            for (TareaRutina x : grupo) {
                x.setOrden(orden);
                orden += 10;
            }
            rutinaRepository.saveAll(grupo);
            for (TareaRutina x : grupo) tareaRepository.borrarDesde(x.getIdrutina(), LocalDate.now(), EstadoTarea.PENDIENTE);
        }
        return grupo.stream().map(this::aDto).collect(Collectors.toList());
    }

    // ── Generación del día ─────────────────────────────────────────────────────

    /** Crea las tareas que falten de ese día en esa tienda. Se puede llamar las veces que sea: no duplica. */
    public void generarDelDia(Integer codti, LocalDate fecha) {
        synchronized (GENERACION) {
            transaccion.executeWithoutResult(s -> crearFaltantes(codti, fecha));
        }
    }

    /** Al encender el servidor: las tareas de hoy de todas las tiendas activas, aunque nadie abra aún la pantalla. */
    public void generarHoyParaTodas() {
        LocalDate hoy = LocalDate.now();
        for (Tienda t : tiendaRepository.findAll()) {
            if (!Boolean.TRUE.equals(t.getActivo())) continue;
            try {
                generarDelDia(t.getCodti(), hoy);
            } catch (RuntimeException e) {
                log.error("No se pudieron generar las tareas de {}", t.getNombre(), e);
            }
        }
    }

    private void crearFaltantes(Integer codti, LocalDate fecha) {
        Tienda tienda = tiendaActiva(codti);
        Set<Integer> ya = new HashSet<>(tareaRepository.rutinasGeneradas(codti, fecha));
        boolean almacen = Boolean.TRUE.equals(tienda.getEsAlmacen());
        for (TareaRutina r : rutinaRepository.activasDe(codti)) {
            if (ya.contains(r.getIdrutina()) || !r.aplicaEn(fecha.getDayOfWeek())) continue;
            // Las actividades de «todas las sucursales» no aplican al almacén; solo las que se arman expresamente para él
            if (almacen && r.getTienda() == null) continue;
            tareaRepository.save(Tarea.builder()
                    .rutina(r)
                    .tienda(tienda)
                    .fecha(fecha)
                    .titulo(r.getTitulo())
                    .descripcion(r.getDescripcion())
                    .momento(r.getMomento())
                    .orden(r.getOrden())
                    .rolDestino(r.getRolDestino())
                    .horaLimite(r.getHoraLimite())
                    .requiereRevision(Boolean.TRUE.equals(r.getRequiereRevision()))
                    .estado(EstadoTarea.PENDIENTE)
                    .build());
        }
    }

    // ── El personal: sus tareas del día ────────────────────────────────────────

    /** Las tareas de hoy que le tocan a quien pregunta (por su rol) en la sucursal en la que trabaja, y sus tareas puntuales. */
    public TareasDiaDto mias(Integer codti, String username) {
        return mias(codti, usuario(username), LocalDateTime.now());
    }

    TareasDiaDto mias(Integer codti, Usuario usuario, LocalDateTime ahora) {
        Tienda tienda = tiendaActiva(codti);
        LocalDate hoy = ahora.toLocalDate();
        generarDelDia(codti, hoy);
        Map<Integer, Tarea> porId = new LinkedHashMap<>();
        for (Tarea t : tareaRepository.delDia(codti, hoy)) porId.put(t.getIdtarea(), t);
        for (Tarea t : tareaRepository.puntualesVisibles(codti, usuario.getIdusuario(), hoy, hoy.plusDays(DIAS_DE_ANTICIPACION)))
            porId.putIfAbsent(t.getIdtarea(), t);
        List<Tarea> tareas = porId.values().stream()
                .filter(t -> puedeVer(usuario, t))
                .sorted(ORDEN_TAREAS)
                .collect(Collectors.toList());
        return dia(tienda, hoy, tareas, ahora);
    }

    @Transactional
    public TareaDto marcarHecha(Integer idtarea, String nota, String username) {
        return marcarHecha(idtarea, nota, usuario(username), LocalDateTime.now());
    }

    TareaDto marcarHecha(Integer idtarea, String nota, Usuario usuario, LocalDateTime ahora) {
        Tarea t = tarea(idtarea);
        if (!puedeVer(usuario, t))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa tarea le toca a otra persona o a otro puesto.");
        // Una actividad de rutina es de su día; una tarea puntual se puede hacer hasta cuando se haga (aunque ya venció)
        if (!t.esPuntual() && !t.getFecha().equals(ahora.toLocalDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden marcar las tareas de hoy.");
        if (t.getEstado() != EstadoTarea.PENDIENTE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya la hizo " + nombreDe(t.getHechaPor()) + " a las "
                    + t.getHechaEl().toLocalTime().truncatedTo(ChronoUnit.MINUTES) + ".");
        String limpia = notaLimpia(nota);
        t.setEstado(Boolean.TRUE.equals(t.getRequiereRevision()) ? EstadoTarea.POR_REVISAR : EstadoTarea.HECHA);
        t.setNota(limpia);
        t.setHechaPor(usuario);
        t.setHechaEl(ahora);
        t.setTarde(vencida(t, ahora));
        t.setComentarioRevision(null);
        t.setRevisadaPor(null);
        t.setRevisadaEl(null);
        return aDto(tareaRepository.save(t), ahora);
    }

    /** Quien la marcó puede deshacerlo el mismo día; también quien da seguimiento a la tienda (encargado, administrador). Una aprobada ya no. */
    @Transactional
    public TareaDto deshacer(Integer idtarea, String username) {
        return deshacer(idtarea, usuario(username), LocalDateTime.now());
    }

    TareaDto deshacer(Integer idtarea, Usuario usuario, LocalDateTime ahora) {
        Tarea t = tarea(idtarea);
        if (t.getEstado() == EstadoTarea.APROBADA)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya está aprobada: no se puede deshacer.");
        if (t.getEstado() == EstadoTarea.PENDIENTE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa tarea no está marcada como hecha.");
        if (t.getHechaEl() == null || !t.getHechaEl().toLocalDate().equals(ahora.toLocalDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden deshacer las tareas marcadas hoy.");
        boolean propia = t.getHechaPor() != null && t.getHechaPor().getIdusuario().equals(usuario.getIdusuario());
        if (!propia && !puedeGestionar(usuario, t.getTienda().getCodti(), Permiso.TAREAS_EQUIPO))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien la marcó o el encargado puede deshacerla.");
        reabrir(t);
        return aDto(tareaRepository.save(t), ahora);
    }

    private static void reabrir(Tarea t) {
        t.setEstado(EstadoTarea.PENDIENTE);
        t.setNota(null);
        t.setHechaPor(null);
        t.setHechaEl(null);
        t.setTarde(null);
    }

    // ── Revisión (quien tiene TAREAS_ASIGNAR) ──────────────────────────────────

    /** Las tareas de la tienda que esperan que alguien las apruebe, de cualquier día. */
    @Transactional(readOnly = true)
    public List<TareaDto> porRevisar(Integer codti, String username) {
        Usuario u = usuario(username);
        tiendaActiva(codti);
        exigirGestor(u, codti, Permiso.TAREAS_ASIGNAR, "Solo el encargado de esa sucursal o un administrador revisa las tareas.");
        LocalDateTime ahora = LocalDateTime.now();
        return tareaRepository.porRevisar(codti).stream().map(t -> aDto(t, ahora)).collect(Collectors.toList());
    }

    @Transactional
    public TareaDto aprobar(Integer idtarea, String username) {
        return aprobar(idtarea, usuario(username), LocalDateTime.now());
    }

    TareaDto aprobar(Integer idtarea, Usuario revisor, LocalDateTime ahora) {
        Tarea t = paraRevisar(idtarea, revisor);
        t.setEstado(EstadoTarea.APROBADA);
        t.setRevisadaPor(revisor);
        t.setRevisadaEl(ahora);
        t.setComentarioRevision(null);
        return aDto(tareaRepository.save(t), ahora);
    }

    /** La regresa: vuelve a quedar pendiente con lo que dijo quien la revisó, para que se haga de nuevo. */
    @Transactional
    public TareaDto rechazar(Integer idtarea, String comentario, String username) {
        return rechazar(idtarea, comentario, usuario(username), LocalDateTime.now());
    }

    TareaDto rechazar(Integer idtarea, String comentario, Usuario revisor, LocalDateTime ahora) {
        String c = comentario == null ? "" : comentario.trim();
        if (c.length() < 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe qué falta o qué corregir.");
        if (c.length() > MAX_NOTA) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comentario es muy largo (máximo " + MAX_NOTA + " letras).");
        Tarea t = paraRevisar(idtarea, revisor);
        reabrir(t);
        t.setRevisadaPor(revisor);
        t.setRevisadaEl(ahora);
        t.setComentarioRevision(c);
        return aDto(tareaRepository.save(t), ahora);
    }

    private Tarea paraRevisar(Integer idtarea, Usuario revisor) {
        Tarea t = tarea(idtarea);
        exigirGestor(revisor, t.getTienda().getCodti(), Permiso.TAREAS_ASIGNAR, "Solo el encargado de esa sucursal o un administrador revisa las tareas.");
        if (t.getEstado() != EstadoTarea.POR_REVISAR)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa tarea no está esperando revisión.");
        if (t.getHechaPor() != null && t.getHechaPor().getIdusuario().equals(revisor.getIdusuario()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lo que hiciste tú lo revisa otra persona.");
        return t;
    }

    // ── Tareas puntuales (las asigna quien tiene TAREAS_ASIGNAR) ───────────────

    /** Quién puede recibir una tarea puntual: el personal activo (encargados, vendedores, técnicos y administradores). */
    @Transactional(readOnly = true)
    public List<PersonaDto> personal(Integer codti, String username) {
        Usuario u = usuario(username);
        tiendaActiva(codti);
        exigirGestor(u, codti, Permiso.TAREAS_ASIGNAR, "Solo el encargado de esa sucursal o un administrador asigna tareas.");
        List<PersonaDto> personas = new ArrayList<>();
        for (Rol r : List.of(Rol.ENCARGADO_TIENDA, Rol.VENDEDOR, Rol.TECNICO, Rol.ADMIN)) {
            for (Usuario x : usuarioRepository.findByRolAndActivoTrue(r)) personas.add(new PersonaDto(x.getIdusuario(), x.getNombreCompleto(), x.getRol().name()));
        }
        personas.sort(Comparator.comparing(PersonaDto::nombre, String.CASE_INSENSITIVE_ORDER));
        return personas;
    }

    @Transactional
    public TareaDto crearPuntual(PuntualRequest req, String username) {
        return crearPuntual(req, usuario(username), LocalDateTime.now());
    }

    TareaDto crearPuntual(PuntualRequest req, Usuario creador, LocalDateTime ahora) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan los datos de la tarea.");
        Tienda tienda = tiendaActiva(req.codti());
        exigirGestor(creador, tienda.getCodti(), Permiso.TAREAS_ASIGNAR, "Solo el encargado de esa sucursal o un administrador asigna tareas.");
        Tarea t = Tarea.builder().tienda(tienda).rutina(null).momento(MomentoTarea.DURANTE).orden(0).estado(EstadoTarea.PENDIENTE)
                .creadaPor(creador.getUsername()).build();
        aplicarPuntual(t, req, ahora.toLocalDate());
        return aDto(tareaRepository.save(t), ahora);
    }

    /** Solo mientras siga pendiente. */
    @Transactional
    public TareaDto actualizarPuntual(Integer idtarea, PuntualRequest req, String username) {
        Usuario u = usuario(username);
        Tarea t = tarea(idtarea);
        exigirPuntualPendiente(t);
        exigirGestor(u, t.getTienda().getCodti(), Permiso.TAREAS_ASIGNAR, "Solo el encargado de esa sucursal o un administrador cambia tareas.");
        LocalDateTime ahora = LocalDateTime.now();
        aplicarPuntual(t, req, null);
        return aDto(tareaRepository.save(t), ahora);
    }

    @Transactional
    public void quitarPuntual(Integer idtarea, String username) {
        Usuario u = usuario(username);
        Tarea t = tarea(idtarea);
        exigirPuntualPendiente(t);
        exigirGestor(u, t.getTienda().getCodti(), Permiso.TAREAS_ASIGNAR, "Solo el encargado de esa sucursal o un administrador quita tareas.");
        tareaRepository.delete(t);
    }

    private static void exigirPuntualPendiente(Tarea t) {
        if (!t.esPuntual()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las actividades de rutina se cambian en «Rutinas».");
        if (t.getEstado() != EstadoTarea.PENDIENTE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa tarea ya se hizo: no se puede cambiar ni quitar.");
    }

    /** @param hoy si se indica, la fecha límite no puede ser anterior (al crear); al cambiar, se respeta la que ya tenía. */
    private void aplicarPuntual(Tarea t, PuntualRequest req, LocalDate hoy) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan los datos de la tarea.");
        String titulo = req.titulo() == null ? "" : req.titulo().trim();
        if (titulo.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe qué hay que hacer.");
        if (titulo.length() > 150) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título es muy largo (máximo 150 letras).");
        String descripcion = req.descripcion() == null || req.descripcion().isBlank() ? null : req.descripcion().trim();
        if (descripcion != null && descripcion.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción es muy larga (máximo 500 letras).");
        if (req.fecha() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la fecha límite.");
        if (hoy != null && req.fecha().isBefore(hoy)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha límite no puede ser anterior a hoy.");

        LocalTime hora = null;
        if (req.horaLimite() != null && !req.horaLimite().isBlank()) {
            try {
                hora = LocalTime.parse(req.horaLimite().trim(), HORA);
            } catch (DateTimeException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora límite debe verse así: 18:30.");
            }
        }

        Usuario asignado = null;
        if (req.idusuarioAsignado() != null) {
            asignado = usuarioRepository.findById(req.idusuarioAsignado())
                    .filter(x -> Boolean.TRUE.equals(x.getActivo()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esa persona no existe o está dada de baja."));
        }
        Rol rol = null;
        if (asignado == null && req.rolDestino() != null && !req.rolDestino().isBlank()) rol = rolDeTarea(req.rolDestino());

        t.setTitulo(titulo);
        t.setDescripcion(descripcion);
        t.setFecha(req.fecha());
        t.setHoraLimite(hora);
        t.setAsignadoA(asignado);
        t.setRolDestino(rol);
        t.setRequiereRevision(Boolean.TRUE.equals(req.requiereRevision()));
    }

    private static Rol rolDeTarea(String texto) {
        Rol rol;
        try {
            rol = Rol.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Puesto no válido: " + texto);
        }
        if (rol != Rol.ENCARGADO_TIENDA && rol != Rol.VENDEDOR && rol != Rol.TECNICO)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La actividad solo puede ser de encargados, vendedores o técnicos.");
        return rol;
    }

    // ── El encargado y el administrador: toda la tienda ────────────────────────

    /**
     * Todas las tareas de un día de la tienda, sin importar a quién le tocan. Sin fecha, hoy; el de hoy trae además lo que sigue abierto de
     * días anteriores (lo que espera revisión y las tareas puntuales vencidas).
     */
    public TareasDiaDto equipo(Integer codti, LocalDate fecha, String username) {
        return equipo(codti, fecha, usuario(username), LocalDateTime.now());
    }

    TareasDiaDto equipo(Integer codti, LocalDate fecha, Usuario usuario, LocalDateTime ahora) {
        Tienda tienda = tiendaActiva(codti);
        exigirGestor(usuario, codti, Permiso.TAREAS_EQUIPO, "Solo el encargado de esa sucursal o un administrador ve las tareas de toda la tienda.");
        LocalDate hoy = ahora.toLocalDate();
        LocalDate dia = fecha != null ? fecha : hoy;
        if (dia.isAfter(hoy)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese día todavía no llega.");
        if (dia.equals(hoy)) generarDelDia(codti, hoy);
        Map<Integer, Tarea> porId = new LinkedHashMap<>();
        for (Tarea t : tareaRepository.delDia(codti, dia)) porId.put(t.getIdtarea(), t);
        if (dia.equals(hoy)) for (Tarea t : tareaRepository.abiertasAnteriores(codti, hoy)) porId.putIfAbsent(t.getIdtarea(), t);
        List<Tarea> tareas = porId.values().stream().sorted(ORDEN_TAREAS).collect(Collectors.toList());
        return dia(tienda, dia, tareas, ahora);
    }

    /**
     * Cuánto se cumplió en un rango de fechas. El administrador puede ver todas las tiendas (sin {@code codti}); el encargado, solo la
     * suya. Las tareas de hoy que siguen abiertas no cuentan como incumplidas: todavía hay tiempo. Una tarea «por revisar» ya la hizo
     * quien la hizo: cuenta como hecha (aparte se dice cuántas esperan revisión).
     */
    @Transactional(readOnly = true)
    public CumplimientoDto cumplimiento(Integer codti, LocalDate desde, LocalDate hasta, String username) {
        return cumplimiento(codti, desde, hasta, usuario(username), LocalDate.now());
    }

    CumplimientoDto cumplimiento(Integer codti, LocalDate desde, LocalDate hasta, Usuario usuario, LocalDate hoy) {
        Integer tienda = codti;
        if (tienda == null && !esAdmin(usuario)) {
            tienda = tiendaRepository.findFirstByIdEncargado(usuario.getIdusuario()).map(Tienda::getCodti).orElse(null);
            if (tienda == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la sucursal.");
        }
        if (tienda != null) exigirGestor(usuario, tienda, Permiso.TAREAS_EQUIPO, "Solo el encargado de esa sucursal o un administrador ve las tareas de toda la tienda.");
        LocalDate h = hasta != null ? hasta : hoy;
        if (h.isAfter(hoy)) h = hoy;
        LocalDate d = desde != null ? desde : h.minusDays(6);
        if (d.isAfter(h)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicial es posterior a la final.");
        if (ChronoUnit.DAYS.between(d, h) >= MAX_DIAS_REPORTE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango máximo es de " + MAX_DIAS_REPORTE + " días.");

        List<Tarea> tareas = tareaRepository.entre(tienda, d, h);

        Map<Integer, List<Tarea>> porTienda = new TreeMap<>();
        for (Tarea t : tareas) porTienda.computeIfAbsent(t.getTienda().getCodti(), k -> new ArrayList<>()).add(t);
        List<TiendaCumplimiento> tiendas = new ArrayList<>();
        for (Map.Entry<Integer, List<Tarea>> e : porTienda.entrySet()) {
            List<Tarea> ts = e.getValue();
            int hechas = (int) ts.stream().filter(Tarea::terminada).count();
            int aTiempo = (int) ts.stream().filter(t -> t.terminada() && !Boolean.TRUE.equals(t.getTarde())).count();
            int porRevisar = (int) ts.stream().filter(t -> t.getEstado() == EstadoTarea.POR_REVISAR).count();
            int pendientesHoy = (int) ts.stream().filter(t -> t.getEstado() == EstadoTarea.PENDIENTE && t.getFecha().equals(hoy)).count();
            int noHechas = (int) ts.stream().filter(t -> t.getEstado() == EstadoTarea.PENDIENTE && t.getFecha().isBefore(hoy)).count();
            int base = ts.size() - pendientesHoy;
            tiendas.add(new TiendaCumplimiento(e.getKey(), ts.get(0).getTienda().getNombre(), ts.size(), hechas, aTiempo, noHechas,
                    pendientesHoy, porcentaje(hechas, base), porcentaje(aTiempo, base), porRevisar));
        }

        Map<Integer, int[]> personas = new LinkedHashMap<>();
        Map<Integer, String> nombres = new HashMap<>();
        for (Tarea t : tareas) {
            if (!t.terminada() || t.getHechaPor() == null) continue;
            int[] c = personas.computeIfAbsent(t.getHechaPor().getIdusuario(), k -> new int[2]);
            c[0]++;
            if (Boolean.TRUE.equals(t.getTarde())) c[1]++;
            nombres.put(t.getHechaPor().getIdusuario(), t.getHechaPor().getNombreCompleto());
        }
        List<PersonaCumplimiento> listaPersonas = personas.entrySet().stream()
                .map(e -> new PersonaCumplimiento(e.getKey(), nombres.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(PersonaCumplimiento::hechas).reversed().thenComparing(PersonaCumplimiento::nombre))
                .collect(Collectors.toList());

        Map<LocalDate, int[]> porDia = new TreeMap<>();
        for (Tarea t : tareas) {
            int[] c = porDia.computeIfAbsent(t.getFecha(), k -> new int[2]);
            c[0]++;
            if (t.terminada()) c[1]++;
        }
        List<DiaCumplimiento> dias = porDia.entrySet().stream()
                .map(e -> new DiaCumplimiento(e.getKey(), e.getValue()[0], e.getValue()[1])).collect(Collectors.toList());

        return new CumplimientoDto(d, h, tienda, tiendas, listaPersonas, dias);
    }

    // ── Reglas de acceso ───────────────────────────────────────────────────────

    private static boolean esAdmin(Usuario u) {
        return u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
    }

    /**
     * El administrador ve todo; los demás, lo que es suyo: una tarea puntual asignada a una persona solo la ve ella; las demás, las de su
     * puesto o las que son de cualquiera.
     */
    private static boolean puedeVer(Usuario u, Tarea t) {
        if (esAdmin(u)) return true;
        if (t.getAsignadoA() != null) return t.getAsignadoA().getIdusuario().equals(u.getIdusuario());
        return t.getRolDestino() == null || t.getRolDestino() == u.getRol();
    }

    /** Administrador: cualquier tienda. Encargado: solo la tienda de la que es el encargado. Otro rol con el permiso: el permiso decide. */
    private boolean puedeGestionar(Usuario u, Integer codti, Permiso permiso) {
        if (esAdmin(u)) return true;
        if (!permisoService.tiene(u.getRol(), permiso.name())) return false;
        if (u.getRol() != Rol.ENCARGADO_TIENDA) return true;
        return tiendaRepository.findById(codti).map(t -> u.getIdusuario().equals(t.getIdEncargado())).orElse(false);
    }

    private void exigirGestor(Usuario u, Integer codti, Permiso permiso, String mensaje) {
        if (!puedeGestionar(u, codti, permiso)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensaje);
    }

    // ── Utilidades ─────────────────────────────────────────────────────────────

    /** ¿Se marcó (o se está marcando) después de su fecha u hora límite? */
    private static boolean vencida(Tarea t, LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        if (t.getFecha().isBefore(hoy)) return true;
        return t.getHoraLimite() != null && t.getFecha().equals(hoy) && ahora.toLocalTime().isAfter(t.getHoraLimite());
    }

    private void aplicar(TareaRutina r, RutinaRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan los datos de la actividad.");
        String titulo = req.titulo() == null ? "" : req.titulo().trim();
        if (titulo.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escriba qué hay que hacer.");
        if (titulo.length() > 150) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título es muy largo (máximo 150 letras).");
        String descripcion = req.descripcion() == null || req.descripcion().isBlank() ? null : req.descripcion().trim();
        if (descripcion != null && descripcion.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción es muy larga (máximo 500 letras).");

        MomentoTarea momento;
        try {
            momento = MomentoTarea.valueOf(req.momento() == null ? "" : req.momento().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elija el momento del día: apertura, durante el día o cierre.");
        }

        Rol rol = null;
        if (req.rolDestino() != null && !req.rolDestino().isBlank()) rol = rolDeTarea(req.rolDestino());

        LocalTime hora = null;
        if (req.horaLimite() != null && !req.horaLimite().isBlank()) {
            try {
                hora = LocalTime.parse(req.horaLimite().trim(), HORA);
            } catch (DateTimeException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora límite debe verse así: 18:30.");
            }
        }

        Tienda tienda = null;
        if (req.codti() != null) tienda = tiendaActiva(req.codti());

        r.setTitulo(titulo);
        r.setDescripcion(descripcion);
        r.setMomento(momento);
        r.setRolDestino(rol);
        r.setHoraLimite(hora);
        r.setTienda(tienda);
        r.setDiasSemana(diasComoTexto(req.dias()));
        if (req.orden() != null) r.setOrden(req.orden());
        if (req.activa() != null) r.setActiva(req.activa());
        r.setRequiereRevision(Boolean.TRUE.equals(req.requiereRevision()));
    }

    private static String diasComoTexto(List<Integer> dias) {
        if (dias == null) return TareaRutina.TODOS_LOS_DIAS;
        if (dias.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elija al menos un día de la semana.");
        TreeSet<Integer> orden = new TreeSet<>();
        for (Integer d : dias) {
            if (d == null || d < 1 || d > 7)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los días van de 1 (lunes) a 7 (domingo).");
            orden.add(d);
        }
        return orden.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private int siguienteOrden(TareaRutina nueva) {
        return rutinaRepository.listar(null).stream()
                .filter(x -> mismaTienda(x, nueva) && x.getMomento() == nueva.getMomento())
                .mapToInt(TareaRutina::getOrden).max().orElse(0) + 10;
    }

    private static boolean mismaTienda(TareaRutina a, TareaRutina b) {
        Integer ta = a.getTienda() == null ? null : a.getTienda().getCodti();
        Integer tb = b.getTienda() == null ? null : b.getTienda().getCodti();
        return Objects.equals(ta, tb);
    }

    private static String notaLimpia(String nota) {
        if (nota == null || nota.isBlank()) return null;
        String n = nota.trim();
        if (n.length() > MAX_NOTA) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nota es muy larga (máximo " + MAX_NOTA + " letras).");
        return n;
    }

    private static Integer porcentaje(int parte, int base) {
        return base <= 0 ? null : (int) Math.round(100.0 * parte / base);
    }

    private static String nombreDe(Usuario u) {
        return u == null ? "otra persona" : u.getNombreCompleto();
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private Tienda tiendaActiva(Integer codti) {
        if (codti == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la sucursal (codti).");
        Tienda t = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sucursal no encontrada: " + codti));
        if (!Boolean.TRUE.equals(t.getActivo())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La sucursal está inactiva.");
        return t;
    }

    private TareaRutina rutina(Integer id) {
        return rutinaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Actividad no encontrada: " + id));
    }

    private Tarea tarea(Integer id) {
        return tareaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea no encontrada: " + id));
    }

    // ── DTO ────────────────────────────────────────────────────────────────────

    private TareasDiaDto dia(Tienda tienda, LocalDate fecha, List<Tarea> tareas, LocalDateTime ahora) {
        List<TareaDto> dtos = tareas.stream().map(t -> aDto(t, ahora)).collect(Collectors.toList());
        int hechas = (int) dtos.stream().filter(t -> !EstadoTarea.PENDIENTE.name().equals(t.estado())).count();
        int atrasadas = (int) dtos.stream().filter(TareaDto::atrasada).count();
        return new TareasDiaDto(tienda.getCodti(), tienda.getNombre(), fecha, dtos.size(), hechas, dtos.size() - hechas, atrasadas, dtos);
    }

    private TareaDto aDto(Tarea t, LocalDateTime ahora) {
        boolean pendiente = t.getEstado() == EstadoTarea.PENDIENTE;
        boolean atrasada = pendiente && (t.getFecha().isBefore(ahora.toLocalDate())
                || (t.getHoraLimite() != null && t.getFecha().equals(ahora.toLocalDate()) && ahora.toLocalTime().isAfter(t.getHoraLimite())));
        return new TareaDto(t.getIdtarea(), t.getTienda().getCodti(), t.getFecha(), t.getTitulo(), t.getDescripcion(), t.getMomento().name(),
                t.getMomento().etiqueta(), t.getOrden(), t.getRolDestino() == null ? null : t.getRolDestino().name(),
                t.getHoraLimite() == null ? null : t.getHoraLimite().toString(), t.getEstado().name(), t.getNota(),
                t.getHechaPor() == null ? null : t.getHechaPor().getIdusuario(), t.getHechaPor() == null ? null : t.getHechaPor().getNombreCompleto(),
                t.getHechaEl(), Boolean.TRUE.equals(t.getTarde()), atrasada,
                t.esPuntual(), t.getAsignadoA() == null ? null : t.getAsignadoA().getIdusuario(),
                t.getAsignadoA() == null ? null : t.getAsignadoA().getNombreCompleto(), Boolean.TRUE.equals(t.getRequiereRevision()),
                t.getRevisadaPor() == null ? null : t.getRevisadaPor().getNombreCompleto(), t.getRevisadaEl(), t.getComentarioRevision());
    }

    private RutinaDto aDto(TareaRutina r) {
        List<Integer> dias = Arrays.stream(r.getDiasSemana().split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::valueOf)
                .collect(Collectors.toList());
        return new RutinaDto(r.getIdrutina(), r.getTienda() == null ? null : r.getTienda().getCodti(),
                r.getTienda() == null ? null : r.getTienda().getNombre(), r.getTitulo(), r.getDescripcion(), r.getMomento().name(),
                r.getMomento().etiqueta(), r.getOrden(), r.getRolDestino() == null ? null : r.getRolDestino().name(), dias,
                r.getHoraLimite() == null ? null : r.getHoraLimite().toString(), Boolean.TRUE.equals(r.getActiva()),
                Boolean.TRUE.equals(r.getRequiereRevision()));
    }
}
