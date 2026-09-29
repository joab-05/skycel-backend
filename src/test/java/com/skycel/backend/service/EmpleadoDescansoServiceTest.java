package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EmpleadoDescanso;
import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.EmpleadoDescansoRepository;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Pruebas unitarias del calendario de descansos de empleados (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class EmpleadoDescansoServiceTest {

    @Mock private EmpleadoDescansoRepository descansoRepository;
    @Mock private EmpleadoPerfilRepository empleadoPerfilRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private EmpleadoDescansoService service;

    private Tienda zocalo, otra;
    private Usuario admin, encargadoZocalo, encargadoOtra;
    private EmpleadoPerfil empleadoZocalo;

    @BeforeEach
    void setUp() {
        zocalo = Tienda.builder().codti(2).nombre("Zócalo").build();
        otra = Tienda.builder().codti(9).nombre("Otra").build();
        admin = Usuario.builder().idusuario(1).username("admin").rol(Rol.ADMIN).build();
        encargadoZocalo = Usuario.builder().idusuario(2).username("enc2").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        encargadoOtra = Usuario.builder().idusuario(3).username("enc9").rol(Rol.ENCARGADO_TIENDA).tienda(otra).build();
        Usuario uEmpleado = Usuario.builder().idusuario(4).nombreCompleto("Juan").tienda(zocalo).build();
        empleadoZocalo = EmpleadoPerfil.builder().idempleado(5).usuario(uEmpleado).build();

        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("enc2")).thenReturn(Optional.of(encargadoZocalo));
        lenient().when(usuarioRepository.findByUsername("enc9")).thenReturn(Optional.of(encargadoOtra));
        lenient().when(empleadoPerfilRepository.findById(5)).thenReturn(Optional.of(empleadoZocalo));
        lenient().when(descansoRepository.save(any(EmpleadoDescanso.class))).thenAnswer(inv -> {
            EmpleadoDescanso d = inv.getArgument(0);
            if (d.getIddescanso() == null) d.setIddescanso(1);
            return d;
        });
    }

    @Test
    @DisplayName("registrar: crea el descanso cuando no hay uno duplicado")
    void registrar_creaDescanso() {
        LocalDate fecha = LocalDate.of(2026, 10, 5);
        when(descansoRepository.existsByEmpleado_IdempleadoAndFecha(5, fecha)).thenReturn(false);

        Map<String, Object> r = service.registrar(5, fecha, "descanso programado", "admin");

        assertThat(r.get("fecha")).isEqualTo(fecha);
        assertThat(r.get("nombreEmpleado")).isEqualTo("Juan");
    }

    @Test
    @DisplayName("registrar: ya existe un descanso en esa fecha -> 409")
    void registrar_duplicado_lanza409() {
        LocalDate fecha = LocalDate.of(2026, 10, 5);
        when(descansoRepository.existsByEmpleado_IdempleadoAndFecha(5, fecha)).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(5, fecha, null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("registrar: sin fecha -> 400")
    void registrar_sinFecha_lanza400() {
        assertThatThrownBy(() -> service.registrar(5, null, null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("registrar: un encargado de otra sucursal no puede registrar descansos de este empleado -> 403")
    void registrar_encargadoOtraSucursal_lanza403() {
        assertThatThrownBy(() -> service.registrar(5, LocalDate.now(), null, "enc9"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("registrar: el encargado de la misma sucursal sí puede")
    void registrar_encargadoMismaSucursal_permite() {
        LocalDate fecha = LocalDate.now();
        when(descansoRepository.existsByEmpleado_IdempleadoAndFecha(5, fecha)).thenReturn(false);

        Map<String, Object> r = service.registrar(5, fecha, null, "enc2");

        assertThat(r.get("iddescanso")).isEqualTo(1);
    }

    @Test
    @DisplayName("eliminar: un encargado de otra sucursal no puede eliminar -> 403")
    void eliminar_encargadoOtraSucursal_lanza403() {
        EmpleadoDescanso d = EmpleadoDescanso.builder().iddescanso(7).empleado(empleadoZocalo).fecha(LocalDate.now()).build();
        when(descansoRepository.findById(7)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.eliminar(7, "enc9"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(descansoRepository, never()).delete(any());
    }
}
