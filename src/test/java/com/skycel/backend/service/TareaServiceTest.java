package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tarea;
import com.skycel.backend.domain.entity.TareaRutina;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.EstadoTarea;
import com.skycel.backend.domain.enums.MomentoTarea;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.tarea.TareaDtos.*;
import com.skycel.backend.repository.TareaRepository;
import com.skycel.backend.repository.TareaRutinaRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@DisplayName("Rutinas y tareas del día")
class TareaServiceTest {

    /** Un lunes. */
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime LUNES_9 = LUNES.atTime(9, 0);

    private final TareaRutinaRepository rutinaRepo = mock(TareaRutinaRepository.class);
    private final TareaRepository tareaRepo = mock(TareaRepository.class);
    private final TiendaRepository tiendaRepo = mock(TiendaRepository.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
    private final PermisoService permisos = mock(PermisoService.class);
    private final TareaService service = new TareaService(rutinaRepo, tareaRepo, tiendaRepo, usuarioRepo, permisos,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final Tienda zocalo = Tienda.builder().codti(2).nombre("Zócalo").activo(true).idEncargado(20).build();
    private final Tienda mina = Tienda.builder().codti(3).nombre("Mina").activo(true).idEncargado(21).build();
    private final Usuario admin = usuario(1, "Joab", Rol.ADMIN);
    private final Usuario encargadoZocalo = usuario(20, "Abigail", Rol.ENCARGADO_TIENDA);
    private final Usuario vendedor = usuario(30, "Luis", Rol.VENDEDOR);
    private final Usuario otroVendedor = usuario(31, "Marta", Rol.VENDEDOR);

    private static Usuario usuario(int id, String nombre, Rol rol) {
        return Usuario.builder().idusuario(id).username("u" + id).nombreCompleto(nombre).rol(rol).activo(true).build();
    }

    private TareaRutina rutina(int id, String titulo, MomentoTarea momento, int orden, Rol rol, String dias) {
        return TareaRutina.builder().idrutina(id).titulo(titulo).momento(momento).orden(orden).rolDestino(rol).diasSemana(dias)
                .activa(true).tienda(zocalo).build();
    }

    private Tarea tarea(int id, String titulo, MomentoTarea momento, int orden, Rol rol, LocalDate fecha) {
        return Tarea.builder().idtarea(id).tienda(zocalo).fecha(fecha).titulo(titulo).momento(momento).orden(orden).rolDestino(rol)
                .estado(EstadoTarea.PENDIENTE).build();
    }

    private static void rechaza(Runnable accion, HttpStatus estado, String texto) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode().value()).isEqualTo(estado.value());
            assertThat(e.getReason()).contains(texto);
        });
    }

    // ── Generación ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("genera la tarea del día solo de las rutinas que aplican ese día de la semana y no repite las ya generadas")
    void generaSoloLoQueAplica() {
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        TareaRutina entreSemana = rutina(1, "Abrir la caja", MomentoTarea.APERTURA, 10, Rol.ENCARGADO_TIENDA, "1,2,3,4,5");
        TareaRutina finDeSemana = rutina(2, "Limpiar la vitrina grande", MomentoTarea.DURANTE, 10, null, "6,7");
        TareaRutina yaGenerada = rutina(3, "Encender vitrinas", MomentoTarea.APERTURA, 20, null, "1,2,3,4,5,6,7");
        when(rutinaRepo.activasDe(2)).thenReturn(List.of(entreSemana, finDeSemana, yaGenerada));
        when(tareaRepo.rutinasGeneradas(2, LUNES)).thenReturn(List.of(3));

        service.generarDelDia(2, LUNES);

        ArgumentCaptor<Tarea> guardada = ArgumentCaptor.forClass(Tarea.class);
        verify(tareaRepo, times(1)).save(guardada.capture());
        Tarea t = guardada.getValue();
        assertThat(t.getTitulo()).isEqualTo("Abrir la caja");
        assertThat(t.getFecha()).isEqualTo(LUNES);
        assertThat(t.getTienda()).isSameAs(zocalo);
        assertThat(t.getRolDestino()).isEqualTo(Rol.ENCARGADO_TIENDA);
        assertThat(t.getEstado()).isEqualTo(EstadoTarea.PENDIENTE);
    }

    @Test
    @DisplayName("en el almacén solo se generan las actividades armadas para él, no las de «todas las sucursales»")
    void almacen() {
        Tienda almacen = Tienda.builder().codti(1).nombre("Almacén").activo(true).esAlmacen(true).build();
        when(tiendaRepo.findById(1)).thenReturn(Optional.of(almacen));
        TareaRutina deTodas = rutina(1, "Abrir la caja", MomentoTarea.APERTURA, 10, null, "1,2,3,4,5,6,7");
        deTodas.setTienda(null);
        TareaRutina propia = rutina(2, "Contar la mercancía recibida", MomentoTarea.DURANTE, 10, null, "1,2,3,4,5,6,7");
        propia.setTienda(almacen);
        when(rutinaRepo.activasDe(1)).thenReturn(List.of(deTodas, propia));

        service.generarDelDia(1, LUNES);

        ArgumentCaptor<Tarea> guardada = ArgumentCaptor.forClass(Tarea.class);
        verify(tareaRepo, times(1)).save(guardada.capture());
        assertThat(guardada.getValue().getTitulo()).isEqualTo("Contar la mercancía recibida");
    }

    @Test
    @DisplayName("no genera tareas para una sucursal inactiva ni inexistente")
    void sucursalInvalida() {
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(Tienda.builder().codti(2).activo(false).build()));
        when(tiendaRepo.findById(99)).thenReturn(Optional.empty());

        rechaza(() -> service.generarDelDia(2, LUNES), HttpStatus.BAD_REQUEST, "inactiva");
        rechaza(() -> service.generarDelDia(99, LUNES), HttpStatus.NOT_FOUND, "no encontrada");
        verify(tareaRepo, never()).save(any());
    }

    @Test
    @DisplayName("al encender el servidor genera las tareas de hoy de las tiendas activas, y una que falle no impide las demás")
    void generaHoyParaTodas() {
        Tienda inactiva = Tienda.builder().codti(4).nombre("Cerrada").activo(false).build();
        when(tiendaRepo.findAll()).thenReturn(List.of(zocalo, inactiva, mina));
        when(tiendaRepo.findById(2)).thenThrow(new IllegalStateException("bd"));
        when(tiendaRepo.findById(3)).thenReturn(Optional.of(mina));
        when(rutinaRepo.activasDe(3)).thenReturn(List.of());

        service.generarHoyParaTodas();

        verify(rutinaRepo).activasDe(3);
        verify(rutinaRepo, never()).activasDe(4);
    }

    // ── Mis tareas ──────────────────────────────────────────────────────────────

    private void hoyHay(Tarea... tareas) {
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        when(rutinaRepo.activasDe(2)).thenReturn(List.of());
        when(tareaRepo.delDia(2, LUNES)).thenReturn(List.of(tareas));
    }

    @Test
    @DisplayName("el vendedor ve las tareas de su puesto y las de cualquiera, en orden de momento y de posición; el administrador ve todas")
    void tareasSegunElRol() {
        Tarea cierre = tarea(1, "Cuadrar la caja", MomentoTarea.CIERRE, 10, null, LUNES);
        Tarea aperturaB = tarea(2, "Acomodar fundas", MomentoTarea.APERTURA, 20, Rol.VENDEDOR, LUNES);
        Tarea aperturaA = tarea(3, "Encender vitrinas", MomentoTarea.APERTURA, 10, null, LUNES);
        Tarea soloEncargado = tarea(4, "Abrir la caja", MomentoTarea.APERTURA, 5, Rol.ENCARGADO_TIENDA, LUNES);
        hoyHay(cierre, aperturaB, aperturaA, soloEncargado);

        TareasDiaDto delVendedor = service.mias(2, vendedor, LUNES_9);
        assertThat(delVendedor.tareas()).extracting(TareaDto::titulo)
                .containsExactly("Encender vitrinas", "Acomodar fundas", "Cuadrar la caja");
        assertThat(delVendedor.total()).isEqualTo(3);
        assertThat(delVendedor.pendientes()).isEqualTo(3);

        TareasDiaDto delAdmin = service.mias(2, admin, LUNES_9);
        assertThat(delAdmin.tareas()).hasSize(4);
        assertThat(delAdmin.tareas().get(0).titulo()).isEqualTo("Abrir la caja");
    }

    @Test
    @DisplayName("una tarea con hora límite ya pasada y sin hacer sale como atrasada")
    void atrasada() {
        Tarea t = tarea(1, "Abrir la caja", MomentoTarea.APERTURA, 10, null, LUNES);
        t.setHoraLimite(LocalTime.of(8, 30));
        hoyHay(t);

        TareasDiaDto dia = service.mias(2, vendedor, LUNES_9);

        assertThat(dia.atrasadas()).isEqualTo(1);
        assertThat(dia.tareas().get(0).atrasada()).isTrue();
        assertThat(service.mias(2, vendedor, LUNES.atTime(8, 0)).atrasadas()).isZero();
    }

    // ── Marcar ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("al marcar una tarea queda quién, cuándo y la nota (sin espacios de más); si pasó su hora límite queda como tarde")
    void marcaHecha() {
        Tarea t = tarea(1, "Cuadrar la caja", MomentoTarea.CIERRE, 10, null, LUNES);
        t.setHoraLimite(LocalTime.of(8, 30));
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));
        when(tareaRepo.save(any(Tarea.class))).thenAnswer(i -> i.getArgument(0));

        TareaDto dto = service.marcarHecha(1, "  faltaban 3 cables  ", vendedor, LUNES_9);

        assertThat(t.getEstado()).isEqualTo(EstadoTarea.HECHA);
        assertThat(t.getHechaPor()).isSameAs(vendedor);
        assertThat(t.getHechaEl()).isEqualTo(LUNES_9);
        assertThat(t.getNota()).isEqualTo("faltaban 3 cables");
        assertThat(t.getTarde()).isTrue();
        assertThat(dto.hechaPor()).isEqualTo("Luis");
        assertThat(dto.tarde()).isTrue();
        assertThat(dto.atrasada()).isFalse();
    }

    @Test
    @DisplayName("si ya la hizo otra persona avisa quién y a qué hora, y no la cambia")
    void yaEstabaHecha() {
        Tarea t = tarea(1, "Cuadrar la caja", MomentoTarea.CIERRE, 10, null, LUNES);
        t.setEstado(EstadoTarea.HECHA);
        t.setHechaPor(otroVendedor);
        t.setHechaEl(LUNES.atTime(8, 45));
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));

        rechaza(() -> service.marcarHecha(1, null, vendedor, LUNES_9), HttpStatus.CONFLICT, "Ya la hizo Marta a las 08:45");
        assertThat(t.getHechaPor()).isSameAs(otroVendedor);
        verify(tareaRepo, never()).save(any());
    }

    @Test
    @DisplayName("solo se marcan las tareas de hoy y las de su puesto, y la nota tiene un largo máximo")
    void reglasAlMarcar() {
        Tarea ayer = tarea(1, "Cuadrar la caja", MomentoTarea.CIERRE, 10, null, LUNES.minusDays(1));
        Tarea delEncargado = tarea(2, "Abrir la caja", MomentoTarea.APERTURA, 10, Rol.ENCARGADO_TIENDA, LUNES);
        Tarea hoy = tarea(3, "Acomodar fundas", MomentoTarea.APERTURA, 20, null, LUNES);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(ayer));
        when(tareaRepo.findById(2)).thenReturn(Optional.of(delEncargado));
        when(tareaRepo.findById(3)).thenReturn(Optional.of(hoy));

        rechaza(() -> service.marcarHecha(1, null, vendedor, LUNES_9), HttpStatus.BAD_REQUEST, "de hoy");
        rechaza(() -> service.marcarHecha(2, null, vendedor, LUNES_9), HttpStatus.FORBIDDEN, "otro puesto");
        rechaza(() -> service.marcarHecha(3, "x".repeat(501), vendedor, LUNES_9), HttpStatus.BAD_REQUEST, "muy larga");
    }

    // ── Deshacer ────────────────────────────────────────────────────────────────

    private Tarea hechaPor(Usuario quien) {
        Tarea t = tarea(1, "Cuadrar la caja", MomentoTarea.CIERRE, 10, null, LUNES);
        t.setEstado(EstadoTarea.HECHA);
        t.setHechaPor(quien);
        t.setHechaEl(LUNES.atTime(8, 0));
        t.setNota("ok");
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));
        when(tareaRepo.save(any(Tarea.class))).thenAnswer(i -> i.getArgument(0));
        return t;
    }

    @Test
    @DisplayName("quien la marcó puede deshacerla y vuelve a quedar pendiente y sin nota")
    void deshacePropia() {
        Tarea t = hechaPor(vendedor);

        service.deshacer(1, vendedor, LUNES_9);

        assertThat(t.getEstado()).isEqualTo(EstadoTarea.PENDIENTE);
        assertThat(t.getHechaPor()).isNull();
        assertThat(t.getNota()).isNull();
        assertThat(t.getHechaEl()).isNull();
    }

    @Test
    @DisplayName("otro vendedor no puede deshacerla; el encargado de esa tienda y el administrador sí")
    void deshaceAjena() {
        Tarea t = hechaPor(vendedor);
        when(permisos.tiene(Rol.VENDEDOR, "TAREAS_EQUIPO")).thenReturn(false);
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_EQUIPO")).thenReturn(true);
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));

        rechaza(() -> service.deshacer(1, otroVendedor, LUNES_9), HttpStatus.FORBIDDEN, "Solo quien la marcó");
        assertThat(t.getEstado()).isEqualTo(EstadoTarea.HECHA);

        service.deshacer(1, encargadoZocalo, LUNES_9);
        assertThat(t.getEstado()).isEqualTo(EstadoTarea.PENDIENTE);

        t.setEstado(EstadoTarea.HECHA);
        t.setHechaPor(vendedor);
        service.deshacer(1, admin, LUNES_9);
        assertThat(t.getEstado()).isEqualTo(EstadoTarea.PENDIENTE);
    }

    @Test
    @DisplayName("el encargado de otra tienda no puede deshacer una tarea que no es de la suya")
    void encargadoDeOtraTienda() {
        hechaPor(vendedor);
        Usuario otroEncargado = usuario(21, "Marely", Rol.ENCARGADO_TIENDA);
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_EQUIPO")).thenReturn(true);
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));

        rechaza(() -> service.deshacer(1, otroEncargado, LUNES_9), HttpStatus.FORBIDDEN, "Solo quien la marcó");
    }

    // ── Toda la tienda ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("el encargado ve las tareas de toda su tienda (de cualquier puesto); el de otra tienda no; el vendedor sin permiso tampoco")
    void equipo() {
        hoyHay(tarea(1, "Abrir la caja", MomentoTarea.APERTURA, 10, Rol.ENCARGADO_TIENDA, LUNES),
                tarea(2, "Acomodar fundas", MomentoTarea.APERTURA, 20, Rol.VENDEDOR, LUNES));
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_EQUIPO")).thenReturn(true);
        when(permisos.tiene(Rol.VENDEDOR, "TAREAS_EQUIPO")).thenReturn(false);
        Usuario otroEncargado = usuario(21, "Marely", Rol.ENCARGADO_TIENDA);

        assertThat(service.equipo(2, null, encargadoZocalo, LUNES_9).tareas()).hasSize(2);
        assertThat(service.equipo(2, null, admin, LUNES_9).tareas()).hasSize(2);
        rechaza(() -> service.equipo(2, null, otroEncargado, LUNES_9), HttpStatus.FORBIDDEN, "encargado de esa sucursal");
        rechaza(() -> service.equipo(2, null, vendedor, LUNES_9), HttpStatus.FORBIDDEN, "encargado de esa sucursal");
        rechaza(() -> service.equipo(2, LUNES.plusDays(1), admin, LUNES_9), HttpStatus.BAD_REQUEST, "todavía no llega");
    }

    @Test
    @DisplayName("un día pasado se consulta sin generar tareas nuevas")
    void diaPasadoNoGenera() {
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        when(tareaRepo.delDia(2, LUNES.minusDays(1))).thenReturn(List.of());

        service.equipo(2, LUNES.minusDays(1), admin, LUNES_9);

        verify(rutinaRepo, never()).activasDe(anyInt());
    }

    // ── Cumplimiento ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("el cumplimiento no cuenta como incumplidas las tareas de hoy que siguen abiertas, y reparte lo hecho por persona")
    void cumplimiento() {
        LocalDate ayer = LUNES.minusDays(1);
        Tarea hechaATiempo = tarea(1, "a", MomentoTarea.APERTURA, 10, null, ayer);
        hechaATiempo.setEstado(EstadoTarea.HECHA);
        hechaATiempo.setHechaPor(vendedor);
        hechaATiempo.setTarde(false);
        Tarea hechaTarde = tarea(2, "b", MomentoTarea.APERTURA, 20, null, ayer);
        hechaTarde.setEstado(EstadoTarea.HECHA);
        hechaTarde.setHechaPor(vendedor);
        hechaTarde.setTarde(true);
        Tarea noHecha = tarea(3, "c", MomentoTarea.CIERRE, 10, null, ayer);
        Tarea hechaHoy = tarea(4, "d", MomentoTarea.APERTURA, 10, null, LUNES);
        hechaHoy.setEstado(EstadoTarea.HECHA);
        hechaHoy.setHechaPor(otroVendedor);
        hechaHoy.setTarde(false);
        Tarea abiertaHoy = tarea(5, "e", MomentoTarea.CIERRE, 10, null, LUNES);
        when(tareaRepo.entre(2, ayer, LUNES)).thenReturn(List.of(hechaATiempo, hechaTarde, noHecha, hechaHoy, abiertaHoy));
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));

        CumplimientoDto c = service.cumplimiento(2, ayer, LUNES, admin, LUNES);

        TiendaCumplimiento t = c.tiendas().get(0);
        assertThat(t.total()).isEqualTo(5);
        assertThat(t.hechas()).isEqualTo(3);
        assertThat(t.aTiempo()).isEqualTo(2);
        assertThat(t.noHechas()).isEqualTo(1);
        assertThat(t.pendientesHoy()).isEqualTo(1);
        assertThat(t.pctHechas()).isEqualTo(75);     // 3 de las 4 que ya vencieron
        assertThat(t.pctATiempo()).isEqualTo(50);    // 2 de 4
        assertThat(c.personas()).extracting(PersonaCumplimiento::nombre).containsExactly("Luis", "Marta");
        assertThat(c.personas().get(0).hechas()).isEqualTo(2);
        assertThat(c.personas().get(0).tarde()).isEqualTo(1);
        assertThat(c.dias()).extracting(DiaCumplimiento::total).containsExactly(3, 2);
    }

    @Test
    @DisplayName("el encargado sin sucursal indicada ve la suya; el administrador sin sucursal ve todas; el rango tiene límite")
    void cumplimientoAlcance() {
        when(tareaRepo.entre(any(), any(), any())).thenReturn(List.of());
        when(tiendaRepo.findFirstByIdEncargado(20)).thenReturn(Optional.of(zocalo));
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_EQUIPO")).thenReturn(true);

        assertThat(service.cumplimiento(null, null, null, encargadoZocalo, LUNES).codti()).isEqualTo(2);
        assertThat(service.cumplimiento(null, null, null, admin, LUNES).codti()).isNull();
        rechaza(() -> service.cumplimiento(2, LUNES.minusDays(200), LUNES, admin, LUNES), HttpStatus.BAD_REQUEST, "rango máximo");
        rechaza(() -> service.cumplimiento(2, LUNES, LUNES.minusDays(2), admin, LUNES), HttpStatus.BAD_REQUEST, "posterior");
    }

    // ── Rutinas ─────────────────────────────────────────────────────────────────

    private static RutinaRequest pide(String titulo, String momento, List<Integer> dias, String hora) {
        return new RutinaRequest(2, titulo, null, momento, null, null, dias, hora, null);
    }

    @Test
    @DisplayName("al crear una actividad se acomoda al final de su momento del día y de su tienda")
    void creaRutina() {
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        when(rutinaRepo.listar(null)).thenReturn(List.of(
                rutina(1, "Abrir la caja", MomentoTarea.APERTURA, 10, null, "1,2,3,4,5,6,7"),
                rutina(2, "Encender vitrinas", MomentoTarea.APERTURA, 30, null, "1,2,3,4,5,6,7"),
                rutina(3, "Cuadrar la caja", MomentoTarea.CIERRE, 90, null, "1,2,3,4,5,6,7")));
        when(rutinaRepo.save(any(TareaRutina.class))).thenAnswer(i -> i.getArgument(0));

        RutinaDto dto = service.crearRutina(pide("  Acomodar fundas ", "apertura", List.of(5, 1, 3), "9:30"), "joab");

        assertThat(dto.titulo()).isEqualTo("Acomodar fundas");
        assertThat(dto.orden()).isEqualTo(40);
        assertThat(dto.dias()).containsExactly(1, 3, 5);
        assertThat(dto.horaLimite()).isEqualTo("09:30");
        assertThat(dto.activa()).isTrue();
        assertThat(dto.nombreTienda()).isEqualTo("Zócalo");
    }

    @Test
    @DisplayName("rechaza una actividad sin título, sin momento, sin días, con hora mal escrita o para un puesto que no existe")
    void validaRutina() {
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));

        rechaza(() -> service.crearRutina(pide("  ", "APERTURA", null, null), "joab"), HttpStatus.BAD_REQUEST, "qué hay que hacer");
        rechaza(() -> service.crearRutina(pide("x", "tarde", null, null), "joab"), HttpStatus.BAD_REQUEST, "momento");
        rechaza(() -> service.crearRutina(pide("x", "APERTURA", List.of(), null), "joab"), HttpStatus.BAD_REQUEST, "al menos un día");
        rechaza(() -> service.crearRutina(pide("x", "APERTURA", List.of(8), null), "joab"), HttpStatus.BAD_REQUEST, "1 (lunes)");
        rechaza(() -> service.crearRutina(pide("x", "APERTURA", null, "25:99"), "joab"), HttpStatus.BAD_REQUEST, "hora");
        rechaza(() -> service.crearRutina(new RutinaRequest(2, "x", null, "APERTURA", null, "ROOT", null, null, null), "joab"),
                HttpStatus.BAD_REQUEST, "solo puede ser de");
        verify(rutinaRepo, never()).save(any());
    }

    @Test
    @DisplayName("al cambiar una actividad se rehacen las tareas de hoy aún sin hacer; al quitarla se desactiva y no se borra")
    void cambiaYQuita() {
        TareaRutina r = rutina(7, "Abrir la caja", MomentoTarea.APERTURA, 10, null, "1,2,3,4,5,6,7");
        when(rutinaRepo.findById(7)).thenReturn(Optional.of(r));
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        when(rutinaRepo.save(any(TareaRutina.class))).thenAnswer(i -> i.getArgument(0));

        RutinaDto dto = service.actualizarRutina(7, pide("Abrir la caja chica", "APERTURA", null, null));
        assertThat(dto.titulo()).isEqualTo("Abrir la caja chica");
        verify(tareaRepo).borrarDesde(eq(7), any(LocalDate.class), eq(EstadoTarea.PENDIENTE));

        service.quitarRutina(7);
        assertThat(r.getActiva()).isFalse();
        verify(tareaRepo, times(2)).borrarDesde(eq(7), any(LocalDate.class), eq(EstadoTarea.PENDIENTE));
        verify(rutinaRepo, never()).delete(any());
    }

    @Test
    @DisplayName("subir una actividad la intercambia con la anterior de su mismo momento y tienda")
    void mueve() {
        TareaRutina a = rutina(1, "A", MomentoTarea.APERTURA, 10, null, "1,2,3,4,5,6,7");
        TareaRutina b = rutina(2, "B", MomentoTarea.APERTURA, 20, null, "1,2,3,4,5,6,7");
        TareaRutina c = rutina(3, "C", MomentoTarea.APERTURA, 30, null, "1,2,3,4,5,6,7");
        TareaRutina otroMomento = rutina(4, "D", MomentoTarea.CIERRE, 10, null, "1,2,3,4,5,6,7");
        when(rutinaRepo.listar(null)).thenReturn(List.of(a, b, c, otroMomento));
        when(rutinaRepo.findById(3)).thenReturn(Optional.of(c));

        List<RutinaDto> r = service.moverRutina(3, -1);

        assertThat(r).extracting(RutinaDto::titulo).containsExactly("A", "C", "B");
        assertThat(c.getOrden()).isEqualTo(20);
        assertThat(b.getOrden()).isEqualTo(30);
        assertThat(otroMomento.getOrden()).isEqualTo(10);
        rechaza(() -> service.moverRutina(3, 5), HttpStatus.BAD_REQUEST, "dirección");
    }
}
