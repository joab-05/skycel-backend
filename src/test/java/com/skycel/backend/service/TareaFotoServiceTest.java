package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tarea;
import com.skycel.backend.domain.entity.TareaFoto;
import com.skycel.backend.domain.entity.TareaRutina;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.EstadoTarea;
import com.skycel.backend.domain.enums.MomentoTarea;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.tarea.TareaDtos.*;
import com.skycel.backend.repository.TareaFotoRepository;
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
import static org.mockito.Mockito.*;

@DisplayName("Tareas: fotos de evidencia")
class TareaFotoServiceTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final LocalDateTime LUNES_9 = LUNES.atTime(9, 0);
    /** Los primeros bytes de un JPEG. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};

    private final TareaRutinaRepository rutinaRepo = mock(TareaRutinaRepository.class);
    private final TareaRepository tareaRepo = mock(TareaRepository.class);
    private final TareaFotoRepository fotoRepo = mock(TareaFotoRepository.class);
    private final TiendaRepository tiendaRepo = mock(TiendaRepository.class);
    private final UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
    private final PermisoService permisos = mock(PermisoService.class);
    private final TareaService service = new TareaService(rutinaRepo, tareaRepo, fotoRepo, tiendaRepo, usuarioRepo, permisos,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final Tienda zocalo = Tienda.builder().codti(2).nombre("Zócalo").activo(true).idEncargado(20).build();
    private final Usuario admin = usuario(1, "Joab", Rol.ADMIN);
    private final Usuario encargado = usuario(20, "Abigail", Rol.ENCARGADO_TIENDA);
    private final Usuario vendedor = usuario(30, "Luis", Rol.VENDEDOR);
    private final Usuario otroVendedor = usuario(31, "Marta", Rol.VENDEDOR);

    private static Usuario usuario(int id, String nombre, Rol rol) {
        return Usuario.builder().idusuario(id).username("u" + id).nombreCompleto(nombre).rol(rol).activo(true).build();
    }

    private Tarea tarea(int id, LocalDate fecha, boolean puntual) {
        return Tarea.builder().idtarea(id).rutina(puntual ? null : TareaRutina.builder().idrutina(1).build()).tienda(zocalo).fecha(fecha).titulo("Limpiar la vitrina")
                .momento(MomentoTarea.DURANTE).orden(10).estado(EstadoTarea.PENDIENTE).build();
    }

    private static void rechaza(Runnable accion, HttpStatus estado, String texto) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode().value()).isEqualTo(estado.value());
            assertThat(e.getReason()).contains(texto);
        });
    }

    private void base() {
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        when(tareaRepo.save(any(Tarea.class))).thenAnswer(i -> i.getArgument(0));
        when(fotoRepo.resumenes(any())).thenReturn(List.of());
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_ASIGNAR")).thenReturn(true);
        when(permisos.tiene(Rol.ENCARGADO_TIENDA, "TAREAS_EQUIPO")).thenReturn(true);
    }

    @Test
    @DisplayName("una tarea que pide foto no se marca sin foto; con una ya se puede")
    void pideFoto() {
        base();
        Tarea t = tarea(1, LUNES, false);
        t.setRequiereFoto(true);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));

        rechaza(() -> service.marcarHecha(1, null, vendedor, LUNES_9), HttpStatus.BAD_REQUEST, "pide una foto");
        assertThat(t.getEstado()).isEqualTo(EstadoTarea.PENDIENTE);

        t.setNumFotos(1);
        assertThat(service.marcarHecha(1, null, vendedor, LUNES_9).estado()).isEqualTo("HECHA");
    }

    @Test
    @DisplayName("subir una foto la guarda con su tipo real (por sus bytes), cuenta las fotos y no pasa de tres")
    void subeFoto() {
        base();
        Tarea t = tarea(1, LUNES, false);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));
        when(fotoRepo.countByTarea_Idtarea(1)).thenReturn(0L, 1L, 1L, 2L, 3L);

        service.subirFoto(1, JPEG, vendedor, LUNES_9);

        org.mockito.ArgumentCaptor<TareaFoto> guardada = org.mockito.ArgumentCaptor.forClass(TareaFoto.class);
        verify(fotoRepo).save(guardada.capture());
        assertThat(guardada.getValue().getTipo()).isEqualTo("image/jpeg");
        assertThat(guardada.getValue().getUsuario()).isSameAs(vendedor);
        assertThat(t.getNumFotos()).isEqualTo(1);

        service.subirFoto(1, PNG, vendedor, LUNES_9);
        verify(fotoRepo, times(2)).save(any(TareaFoto.class));
        rechaza(() -> service.subirFoto(1, JPEG, vendedor, LUNES_9), HttpStatus.BAD_REQUEST, "hasta 3 fotos");
    }

    @Test
    @DisplayName("no se aceptan fotos vacías, enormes ni que no sean JPEG o PNG")
    void validaFoto() {
        base();
        Tarea t = tarea(1, LUNES, false);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));
        when(fotoRepo.countByTarea_Idtarea(1)).thenReturn(0L);

        rechaza(() -> service.subirFoto(1, new byte[0], vendedor, LUNES_9), HttpStatus.BAD_REQUEST, "vacía");
        rechaza(() -> service.subirFoto(1, new byte[TareaService.MAX_BYTES_FOTO + 1], vendedor, LUNES_9), HttpStatus.PAYLOAD_TOO_LARGE, "pesa demasiado");
        rechaza(() -> service.subirFoto(1, "esto no es una imagen".getBytes(), vendedor, LUNES_9), HttpStatus.BAD_REQUEST, "JPEG o PNG");
        verify(fotoRepo, never()).save(any(TareaFoto.class));
    }

    @Test
    @DisplayName("las fotos se suben solo a una tarea pendiente, de hoy si es de rutina, y que le toque a quien la sube")
    void cuandoSeSube() {
        base();
        Tarea hecha = tarea(1, LUNES, false);
        hecha.setEstado(EstadoTarea.HECHA);
        Tarea deAyer = tarea(2, LUNES.minusDays(1), false);
        Tarea puntualVencida = tarea(3, LUNES.minusDays(2), true);
        Tarea deOtro = tarea(4, LUNES, true);
        deOtro.setAsignadoA(otroVendedor);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(hecha));
        when(tareaRepo.findById(2)).thenReturn(Optional.of(deAyer));
        when(tareaRepo.findById(3)).thenReturn(Optional.of(puntualVencida));
        when(tareaRepo.findById(4)).thenReturn(Optional.of(deOtro));
        when(fotoRepo.countByTarea_Idtarea(any())).thenReturn(0L);

        rechaza(() -> service.subirFoto(1, JPEG, vendedor, LUNES_9), HttpStatus.CONFLICT, "pendiente");
        rechaza(() -> service.subirFoto(2, JPEG, vendedor, LUNES_9), HttpStatus.BAD_REQUEST, "de hoy");
        rechaza(() -> service.subirFoto(4, JPEG, vendedor, LUNES_9), HttpStatus.FORBIDDEN, "otra persona");
        service.subirFoto(3, JPEG, vendedor, LUNES_9);      // una tarea puntual vencida sí
        verify(fotoRepo, times(1)).save(any(TareaFoto.class));
    }

    @Test
    @DisplayName("las fotos las ve quien hace la tarea y quien da seguimiento a la tienda; otro vendedor no")
    void quienVe() {
        base();
        Tarea t = tarea(1, LUNES, true);
        t.setAsignadoA(vendedor);
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));
        when(usuarioRepo.findByUsername("u30")).thenReturn(Optional.of(vendedor));
        when(usuarioRepo.findByUsername("u31")).thenReturn(Optional.of(otroVendedor));
        when(usuarioRepo.findByUsername("u20")).thenReturn(Optional.of(encargado));
        when(permisos.tiene(Rol.VENDEDOR, "TAREAS_EQUIPO")).thenReturn(false);
        when(fotoRepo.resumenes(1)).thenReturn(List.<Object[]>of(new Object[]{7, "image/jpeg", LUNES_9, 30, "Luis"}));

        assertThat(service.fotos(1, "u30")).hasSize(1);
        assertThat(service.fotos(1, "u20")).extracting(FotoDto::usuario).containsExactly("Luis");
        rechaza(() -> service.fotos(1, "u31"), HttpStatus.FORBIDDEN, "No puedes ver");
    }

    @Test
    @DisplayName("borra la foto quien la subió mientras la tarea está pendiente, o el encargado; otro vendedor no")
    void borraFoto() {
        base();
        Tarea t = tarea(1, LUNES, true);
        t.setAsignadoA(vendedor);
        t.setNumFotos(1);
        TareaFoto f = TareaFoto.builder().idfoto(7).tarea(t).usuario(vendedor).tipo("image/jpeg").contenido(JPEG).build();
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));
        when(fotoRepo.findById(7)).thenReturn(Optional.of(f));
        when(usuarioRepo.findByUsername("u30")).thenReturn(Optional.of(vendedor));
        when(usuarioRepo.findByUsername("u31")).thenReturn(Optional.of(otroVendedor));
        when(usuarioRepo.findByUsername("u20")).thenReturn(Optional.of(encargado));
        when(tiendaRepo.findById(2)).thenReturn(Optional.of(zocalo));
        when(fotoRepo.countByTarea_Idtarea(1)).thenReturn(0L);

        rechaza(() -> service.eliminarFoto(1, 7, "u31"), HttpStatus.FORBIDDEN, "quien la subió");
        service.eliminarFoto(1, 7, "u30");
        verify(fotoRepo).delete(f);
        assertThat(t.getNumFotos()).isZero();

        // ya hecha: quien la subió ya no; el encargado sí
        t.setEstado(EstadoTarea.POR_REVISAR);
        rechaza(() -> service.eliminarFoto(1, 7, "u30"), HttpStatus.FORBIDDEN, "quien la subió");
        service.eliminarFoto(1, 7, "u20");
        verify(fotoRepo, times(2)).delete(f);
    }

    @Test
    @DisplayName("una foto que no es de esa tarea no existe")
    void fotoAjena() {
        base();
        Tarea t = tarea(1, LUNES, true);
        Tarea otra = tarea(2, LUNES, true);
        TareaFoto f = TareaFoto.builder().idfoto(7).tarea(otra).tipo("image/jpeg").contenido(JPEG).build();
        when(tareaRepo.findById(1)).thenReturn(Optional.of(t));
        when(fotoRepo.findById(7)).thenReturn(Optional.of(f));
        when(usuarioRepo.findByUsername("u1")).thenReturn(Optional.of(admin));

        rechaza(() -> service.foto(1, 7, "u1"), HttpStatus.NOT_FOUND, "no existe");
    }

    @Test
    @DisplayName("quitar una tarea puntual borra antes sus fotos; al regenerar las pendientes de una rutina también")
    void borraFotosAlQuitar() {
        base();
        Tarea p = tarea(10, LUNES, true);
        when(tareaRepo.findById(10)).thenReturn(Optional.of(p));
        when(usuarioRepo.findByUsername("u1")).thenReturn(Optional.of(admin));

        service.quitarPuntual(10, "u1");

        verify(fotoRepo).borrarDeTarea(10);
        verify(tareaRepo).delete(p);

        TareaRutina r = TareaRutina.builder().idrutina(5).titulo("x").momento(MomentoTarea.APERTURA).diasSemana("1,2,3,4,5,6,7").activa(true).build();
        when(rutinaRepo.findById(5)).thenReturn(Optional.of(r));
        service.quitarRutina(5);
        verify(fotoRepo).borrarPendientesDeRutina(org.mockito.ArgumentMatchers.eq(5), any(LocalDate.class));
    }

    @Test
    @DisplayName("las rutinas y las tareas puntuales llevan la marca de que piden foto")
    void marcaDeFoto() {
        base();
        when(rutinaRepo.save(any(TareaRutina.class))).thenAnswer(i -> i.getArgument(0));
        RutinaDto dto = service.crearRutina(new RutinaRequest(2, "Fotografiar la vitrina", null, "CIERRE", null, null, null, null, null, null, true), "joab");
        assertThat(dto.requiereFoto()).isTrue();

        TareaDto t = service.crearPuntual(new PuntualRequest(2, "Fotografiar el aparador", null, LUNES, null, null, null, null, true), admin, LUNES_9);
        assertThat(t.requiereFoto()).isTrue();
        assertThat(t.fotos()).isZero();
    }
}
