package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tarea;
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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Tareas: revisión y tareas puntuales")
class TareaRevisionServiceTest {

    /** Un lunes. */
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime LUNES_9 = LUNES.atTime(9, 0);

    private final TareaRutinaRepository rutinaRepo = mock(TareaRutinaRepository.class);
    private final TareaRepository tareaRepo = mock(TareaRepository.class);
    private final com.skycel.backend.repository.TareaFotoRepository fotoRepo = mock(com.skycel.backend.repository.TareaFotoRepository.class);
    private final TiendaRepository tiendaRepo = mock(TiendaRepository.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
    private final PermisoService permisos = mock(PermisoService.class);
    private final TareaService service = new TareaService(rutinaRepo, tareaRepo, fotoRepo, tiendaRepo, usuarioRepo, permisos,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final Tienda zocalo = Tienda.builder().codti(2).nombre("Zócalo").activo(true).idEncargado(20).build();
    private final Usuario admin = usuario(1, "Joab", Rol.ADMIN);
    private final Usuario encargadoZocalo = usuario(20, "Abigail", Rol.ENCARGADO_TIENDA);
    private final Usuario otroEncargado = usuario(21, "Marely", Rol.ENCARGADO_TIENDA);
    private final Usuario vendedor = usuario(30, "Luis", Rol.VENDEDOR);
    private final Usuario otroVendedor = usuario(31, "Marta", Rol.VENDEDOR);

    private static Usuario usuario(int id, String nombre, Rol rol) {
        return Usuario.builder().idusuario(id).username("u" + id).nombreCompleto(nombre).rol(rol).activo(true).build();
    }

    private Tarea tarea(int id, String titulo, MomentoTarea momento, LocalDate fecha) {
        return Tarea.builder().idtarea(id).tienda(zocalo).fecha(fecha).titulo(titulo).momento(momento).orden(10)
                .estado(EstadoTarea.PENDIENTE).build();
    }

    private Tarea puntual(int id, String titulo, LocalDate fecha, Usuario asignado) {
        Tarea t = tarea(id, titulo, MomentoTarea.DURANTE, fecha);
        t.setOrden(0);
        t.setAsignadoA(asignado);
        return t;
    }

    private Tarea deRutina(int id, String titulo, LocalDate fecha) {
        Tarea t = tarea(id, titulo, MomentoTarea.APERTURA, fecha);
        t.setRutina(com.skycel.backend.domain.entity.TareaRutina.builder().idrutina(1).build());
        return t;
    }

    private static void rechaza(Runnable accion, HttpStatus estado, String texto) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode().value()).isEqualTo(estado.value());
            assertThat(e.getReason()).contains(texto);
        });
    }

    private void preparar() {
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_ASIGNAR")).thenReturn(true);
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_EQUIPO")).thenReturn(true);
        when(permisos.tiene(Rol.VENDEDOR, "TAREAS_ASIGNAR")).thenReturn(false);
        when(tareaRepo.save(any(Tarea.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("una actividad que pide revisión queda «por revisar» al marcarla; el encargado la aprueba y quien la hizo no la revisa")
    void marcaYQuedaPorRevisar() {
        preparar();
        Tarea t = deRutina(1, "Cuadrar la caja", LUNES);
        t.setRequiereRevision(true);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));

        TareaDto dto = service.marcarHecha(1, "listo", vendedor, LUNES_9);

        assertThat(t.getEstado()).isEqualTo(EstadoTarea.POR_REVISAR);
        assertThat(dto.estado()).isEqualTo("POR_REVISAR");
        rechaza(() -> service.aprobar(1, vendedor, LUNES_9), HttpStatus.FORBIDDEN, "revisa las tareas");

        TareaDto aprobada = service.aprobar(1, encargadoZocalo, LUNES_9.plusHours(1));
        assertThat(aprobada.estado()).isEqualTo("APROBADA");
        assertThat(t.getRevisadaPor()).isSameAs(encargadoZocalo);
        rechaza(() -> service.aprobar(1, encargadoZocalo, LUNES_9), HttpStatus.CONFLICT, "no está esperando revisión");
        rechaza(() -> service.deshacer(1, vendedor, LUNES_9), HttpStatus.CONFLICT, "Ya está aprobada");
    }

    @Test
    @DisplayName("quien hizo la tarea no la revisa, aunque tenga permiso")
    void nadieSeRevisaASiMismo() {
        preparar();
        Tarea t = deRutina(1, "x", LUNES);
        t.setEstado(EstadoTarea.POR_REVISAR);
        t.setHechaPor(encargadoZocalo);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));

        rechaza(() -> service.aprobar(1, encargadoZocalo, LUNES_9), HttpStatus.FORBIDDEN, "lo revisa otra persona");
        assertThat(service.aprobar(1, admin, LUNES_9).estado()).isEqualTo("APROBADA");
    }

    @Test
    @DisplayName("rechazar pide un comentario y deja la tarea pendiente de nuevo, con lo que se dijo; al volver a marcarla se limpia")
    void rechaza() {
        preparar();
        Tarea t = deRutina(1, "Acomodar fundas", LUNES);
        t.setEstado(EstadoTarea.POR_REVISAR);
        t.setHechaPor(vendedor);
        t.setHechaEl(LUNES.atTime(8, 0));
        t.setNota("ya");
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));

        rechaza(() -> service.rechazar(1, "  ", encargadoZocalo, LUNES_9), HttpStatus.BAD_REQUEST, "qué falta");
        TareaDto dto = service.rechazar(1, " faltan los cables ", encargadoZocalo, LUNES_9);

        assertThat(t.getEstado()).isEqualTo(EstadoTarea.PENDIENTE);
        assertThat(t.getHechaPor()).isNull();
        assertThat(t.getNota()).isNull();
        assertThat(dto.comentarioRevision()).isEqualTo("faltan los cables");
        assertThat(dto.revisadaPor()).isEqualTo("Abigail");

        service.marcarHecha(1, null, vendedor, LUNES_9.plusHours(2));
        assertThat(t.getComentarioRevision()).isNull();
    }

    @Test
    @DisplayName("revisar es del encargado de esa tienda o del administrador: un vendedor o el encargado de otra tienda no")
    void quienRevisa() {
        preparar();
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_ASIGNAR")).thenReturn(true);
        Tarea t = deRutina(1, "x", LUNES);
        t.setEstado(EstadoTarea.POR_REVISAR);
        t.setHechaPor(vendedor);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));

        rechaza(() -> service.aprobar(1, otroVendedor, LUNES_9), HttpStatus.FORBIDDEN, "revisa las tareas");
        rechaza(() -> service.aprobar(1, otroEncargado, LUNES_9), HttpStatus.FORBIDDEN, "revisa las tareas");
    }

    @Test
    @DisplayName("una tarea puntual solo la ve la persona asignada (y los administradores); se puede hacer aunque ya venció, y queda como tarde")
    void puntualAsignada() {
        preparar();
        LocalDate ayer = LUNES.minusDays(1);
        Tarea mia = puntual(10, "Llamar al cliente de la orden", ayer, vendedor);
        Tarea deOtro = puntual(11, "Contar fundas", LUNES, otroVendedor);
        Tarea delPuesto = puntual(12, "Revisar vitrinas", LUNES, null);
        delPuesto.setRolDestino(Rol.ENCARGADO_TIENDA);
        when(tareaRepo.puntualesVisibles(eq(2), eq(30), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(mia, deOtro, delPuesto));
        when(tareaRepo.puntualesVisibles(eq(2), eq(1), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(mia, deOtro, delPuesto));
        when(rutinaRepo.activasDe(2)).thenReturn(List.of());

        TareasDiaDto delVendedor = service.mias(2, vendedor, LUNES_9);
        assertThat(delVendedor.tareas()).extracting(TareaDto::titulo).containsExactly("Llamar al cliente de la orden");
        assertThat(delVendedor.tareas().get(0).puntual()).isTrue();
        assertThat(delVendedor.tareas().get(0).atrasada()).isTrue();      // venció ayer y sigue pendiente
        assertThat(service.mias(2, admin, LUNES_9).tareas()).hasSize(3);

        when(tareaRepo.findById(10)).thenReturn(Optional.of(mia));
        when(tareaRepo.findById(11)).thenReturn(Optional.of(deOtro));
        TareaDto hecha = service.marcarHecha(10, null, vendedor, LUNES_9);   // se puede aunque la fecha límite ya pasó
        assertThat(hecha.estado()).isEqualTo("HECHA");
        assertThat(hecha.tarde()).isTrue();
        rechaza(() -> service.marcarHecha(11, null, vendedor, LUNES_9), HttpStatus.FORBIDDEN, "otra persona");
    }

    private PuntualRequest pide(String titulo, LocalDate fecha, Integer persona) {
        return new PuntualRequest(2, titulo, null, fecha, null, persona, null, true, null);
    }

    @Test
    @DisplayName("crear una tarea puntual valida título, fecha, hora y persona, y solo la crea el encargado de esa tienda o el administrador")
    void creaPuntual() {
        preparar();
        when(usuarioRepo.findById(30)).thenReturn(Optional.of(vendedor));
        when(usuarioRepo.findById(99)).thenReturn(Optional.empty());

        TareaDto dto = service.crearPuntual(pide("  Contar fundas ", LUNES, 30), encargadoZocalo, LUNES_9);
        assertThat(dto.titulo()).isEqualTo("Contar fundas");
        assertThat(dto.puntual()).isTrue();
        assertThat(dto.idusuarioAsignado()).isEqualTo(30);
        assertThat(dto.requiereRevision()).isTrue();
        assertThat(dto.estado()).isEqualTo("PENDIENTE");

        rechaza(() -> service.crearPuntual(pide(" ", LUNES, 30), admin, LUNES_9), HttpStatus.BAD_REQUEST, "qué hay que hacer");
        rechaza(() -> service.crearPuntual(pide("x", null, 30), admin, LUNES_9), HttpStatus.BAD_REQUEST, "fecha límite");
        rechaza(() -> service.crearPuntual(pide("x", LUNES.minusDays(1), 30), admin, LUNES_9), HttpStatus.BAD_REQUEST, "anterior a hoy");
        rechaza(() -> service.crearPuntual(pide("x", LUNES, 99), admin, LUNES_9), HttpStatus.BAD_REQUEST, "no existe");
        rechaza(() -> service.crearPuntual(new PuntualRequest(2, "x", null, LUNES, "25:99", null, null, false, null), admin, LUNES_9), HttpStatus.BAD_REQUEST, "hora límite");
        rechaza(() -> service.crearPuntual(pide("x", LUNES, 30), vendedor, LUNES_9), HttpStatus.FORBIDDEN, "asigna tareas");
        rechaza(() -> service.crearPuntual(pide("x", LUNES, 30), otroEncargado, LUNES_9), HttpStatus.FORBIDDEN, "asigna tareas");
    }

    @Test
    @DisplayName("una tarea puntual se cambia o se quita solo mientras siga pendiente; las de rutina no")
    void cambiaYQuitaPuntual() {
        preparar();
        when(usuarioRepo.findByUsername("u1")).thenReturn(Optional.of(admin));
        Tarea p = puntual(10, "Contar fundas", LUNES, null);
        Tarea hecha = puntual(11, "Otra", LUNES, null);
        hecha.setEstado(EstadoTarea.HECHA);
        Tarea deRutina = deRutina(12, "Abrir la caja", LUNES);
        when(tareaRepo.findById(10)).thenReturn(Optional.of(p));
        when(tareaRepo.findById(11)).thenReturn(Optional.of(hecha));
        when(tareaRepo.findById(12)).thenReturn(Optional.of(deRutina));

        TareaDto dto = service.actualizarPuntual(10, new PuntualRequest(2, "Contar fundas y cables", null, LUNES.plusDays(1), null, null, "VENDEDOR", false, null), "u1");
        assertThat(dto.titulo()).isEqualTo("Contar fundas y cables");
        assertThat(dto.rolDestino()).isEqualTo("VENDEDOR");
        service.quitarPuntual(10, "u1");
        verify(tareaRepo).delete(p);

        rechaza(() -> service.quitarPuntual(11, "u1"), HttpStatus.CONFLICT, "ya se hizo");
        rechaza(() -> service.actualizarPuntual(12, pide("x", LUNES, null), "u1"), HttpStatus.BAD_REQUEST, "Rutinas");
    }

    @Test
    @DisplayName("el día de hoy del equipo trae además lo que sigue abierto de días anteriores (por revisar y puntuales vencidas), las puntuales primero")
    void equipoTraeAbiertasAnteriores() {
        preparar();
        Tarea porRevisarAyer = deRutina(1, "Cuadrar la caja", LUNES.minusDays(1));
        porRevisarAyer.setEstado(EstadoTarea.POR_REVISAR);
        Tarea puntualVencida = puntual(2, "Llamar al cliente", LUNES.minusDays(2), null);
        when(rutinaRepo.activasDe(2)).thenReturn(List.of());
        when(tareaRepo.delDia(2, LUNES)).thenReturn(List.of(deRutina(3, "Abrir la caja", LUNES)));
        when(tareaRepo.abiertasAnteriores(2, LUNES)).thenReturn(List.of(porRevisarAyer, puntualVencida));

        TareasDiaDto d = service.equipo(2, null, admin, LUNES_9);

        assertThat(d.tareas()).hasSize(3);
        assertThat(d.tareas().get(0).titulo()).isEqualTo("Llamar al cliente");
        assertThat(d.atrasadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("el cumplimiento cuenta como hecha una tarea por revisar o aprobada, y dice cuántas esperan revisión")
    void cumplimientoConRevision() {
        preparar();
        LocalDate ayer = LUNES.minusDays(1);
        Tarea aprobada = deRutina(1, "a", ayer);
        aprobada.setEstado(EstadoTarea.APROBADA);
        aprobada.setHechaPor(vendedor);
        aprobada.setTarde(false);
        Tarea porRevisar = deRutina(2, "b", ayer);
        porRevisar.setEstado(EstadoTarea.POR_REVISAR);
        porRevisar.setHechaPor(vendedor);
        porRevisar.setTarde(false);
        Tarea noHecha = deRutina(3, "c", ayer);
        when(tareaRepo.entre(2, ayer, LUNES)).thenReturn(List.of(aprobada, porRevisar, noHecha));

        CumplimientoDto c = service.cumplimiento(2, ayer, LUNES, admin, LUNES);

        TiendaCumplimiento t = c.tiendas().get(0);
        assertThat(t.hechas()).isEqualTo(2);
        assertThat(t.porRevisar()).isEqualTo(1);
        assertThat(t.noHechas()).isEqualTo(1);
        assertThat(t.pctHechas()).isEqualTo(67);
        assertThat(c.personas().get(0).hechas()).isEqualTo(2);
    }

    @Test
    @DisplayName("el personal disponible son los empleados activos, por nombre")
    void personal() {
        preparar();
        when(usuarioRepo.findByUsername("u1")).thenReturn(Optional.of(admin));
        when(usuarioRepo.findByRolAndActivoTrue(Rol.ENCARGADO_TIENDA)).thenReturn(List.of(encargadoZocalo));
        when(usuarioRepo.findByRolAndActivoTrue(Rol.VENDEDOR)).thenReturn(List.of(vendedor, otroVendedor));
        when(usuarioRepo.findByRolAndActivoTrue(Rol.TECNICO)).thenReturn(List.of());
        when(usuarioRepo.findByRolAndActivoTrue(Rol.ADMIN)).thenReturn(List.of(admin));

        List<PersonaDto> r = service.personal(2, "u1");

        assertThat(r).extracting(PersonaDto::nombre).containsExactly("Abigail", "Joab", "Luis", "Marta");
    }

    @Test
    @DisplayName("la lista de por revisar es de quien revisa esa tienda")
    void listaPorRevisar() {
        preparar();
        when(usuarioRepo.findByUsername("u20")).thenReturn(Optional.of(encargadoZocalo));
        Tarea t = deRutina(1, "x", LUNES);
        t.setEstado(EstadoTarea.POR_REVISAR);
        t.setHechaEl(LUNES.atTime(18, 0));
        when(tareaRepo.porRevisar(2)).thenReturn(List.of(t));

        assertThat(service.porRevisar(2, "u20")).hasSize(1);
        when(usuarioRepo.findByUsername("u30")).thenReturn(Optional.of(vendedor));
        rechaza(() -> service.porRevisar(2, "u30"), HttpStatus.FORBIDDEN, "revisa las tareas");
    }
}
