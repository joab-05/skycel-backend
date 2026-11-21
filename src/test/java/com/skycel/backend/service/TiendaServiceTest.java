package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.dto.usuario.UsuarioCreateDto;
import com.skycel.backend.dto.usuario.UsuarioResponseDto;
import com.skycel.backend.dto.usuario.UsuarioUpdateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TiendaServiceTest {

    @Mock TiendaRepository tiendaRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock EmpleadoPerfilRepository empleadoPerfilRepository;
    @Mock PasswordEncoder passwordEncoder;

    TiendaService service;
    UsuarioService usuarios;

    Tienda zocalo, superche;
    Usuario ana, beto, vendedor;

    @BeforeEach
    void preparar() {
        service = new TiendaService(tiendaRepository, usuarioRepository);
        usuarios = new UsuarioService(usuarioRepository, empleadoPerfilRepository, tiendaRepository, service, passwordEncoder);
        zocalo = Tienda.builder().codti(1).nombre("Zócalo").activo(true).build();
        superche = Tienda.builder().codti(2).nombre("Superche").activo(true).build();
        ana = Usuario.builder().idusuario(10).username("ana").nombreCompleto("Ana").rol(Rol.ENCARGADO_TIENDA).activo(true).build();
        beto = Usuario.builder().idusuario(11).username("beto").nombreCompleto("Beto").rol(Rol.ENCARGADO_TIENDA).activo(true).build();
        vendedor = Usuario.builder().idusuario(12).username("vic").nombreCompleto("Vic").rol(Rol.VENDEDOR).activo(true).build();
        lenient().when(tiendaRepository.findById(1)).thenReturn(Optional.of(zocalo));
        lenient().when(tiendaRepository.findById(2)).thenReturn(Optional.of(superche));
        lenient().when(tiendaRepository.save(any(Tienda.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(usuarioRepository.findById(10)).thenReturn(Optional.of(ana));
        lenient().when(usuarioRepository.findById(11)).thenReturn(Optional.of(beto));
        lenient().when(usuarioRepository.findById(12)).thenReturn(Optional.of(vendedor));
        lenient().when(tiendaRepository.findFirstByIdEncargado(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("designar al encargado le da la tienda; el anterior se queda sin ella")
    void asignarYReemplazar() {
        Tienda t = service.asignarEncargado(1, 10);
        assertThat(t.getIdEncargado()).isEqualTo(10);
        assertThat(t.getNombreEncargado()).isEqualTo("Ana");
        assertThat(ana.getTienda()).isSameAs(zocalo);

        // llega Beto: Ana deja de tener tienda
        service.asignarEncargado(1, 11);
        assertThat(zocalo.getIdEncargado()).isEqualTo(11);
        assertThat(beto.getTienda()).isSameAs(zocalo);
        assertThat(ana.getTienda()).isNull();
    }

    @Test
    @DisplayName("quitar al encargado (null) deja la tienda sin encargado y a él sin tienda")
    void quitar() {
        service.asignarEncargado(1, 10);
        service.asignarEncargado(1, null);
        assertThat(zocalo.getIdEncargado()).isNull();
        assertThat(ana.getTienda()).isNull();
    }

    @Test
    @DisplayName("solo un encargado de tienda activo, y no de dos tiendas a la vez")
    void validaciones() {
        assertThatThrownBy(() -> service.asignarEncargado(1, 12))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("rol de encargado");
        ana.setActivo(false);
        assertThatThrownBy(() -> service.asignarEncargado(1, 10))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("desactivado");
        ana.setActivo(true);

        zocalo.setIdEncargado(10);
        when(tiendaRepository.findFirstByIdEncargado(10)).thenReturn(Optional.of(zocalo));
        assertThatThrownBy(() -> service.asignarEncargado(2, 10))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya es el encargado de Zócalo");
        assertThat(superche.getIdEncargado()).isNull();
    }

    @Test
    @DisplayName("marcar el taller desmarca el anterior y manda a los técnicos a él")
    void taller() {
        Usuario tecnico = Usuario.builder().idusuario(20).username("tec").nombreCompleto("Tec").rol(Rol.TECNICO).activo(true).tienda(zocalo).build();
        zocalo.setEsTaller(true);
        when(tiendaRepository.findByEsTallerTrue()).thenReturn(List.of(zocalo));
        when(usuarioRepository.findByRolAndActivoTrue(Rol.TECNICO)).thenReturn(List.of(tecnico));

        service.marcarTaller(2, true);

        assertThat(zocalo.getEsTaller()).isFalse();
        assertThat(superche.getEsTaller()).isTrue();
        assertThat(tecnico.getTienda()).isSameAs(superche);
    }

    @Test
    @DisplayName("un empleado nuevo ya no trae tienda: el vendedor no tiene; el técnico queda en el taller; codti se ignora")
    void empleadoNuevoSinTienda() {
        when(usuarioRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        superche.setEsTaller(true);
        when(tiendaRepository.findByEsTallerTrue()).thenReturn(List.of(superche));

        UsuarioCreateDto v = new UsuarioCreateDto();
        v.setUsername("nuevo"); v.setPassword("x"); v.setNombreCompleto("Nuevo"); v.setRol("VENDEDOR"); v.setCodti(1);
        UsuarioResponseDto r = usuarios.crear(v);
        assertThat(r.getCodti()).isNull();

        UsuarioCreateDto t = new UsuarioCreateDto();
        t.setUsername("tec2"); t.setPassword("x"); t.setNombreCompleto("Tec2"); t.setRol("TECNICO");
        UsuarioResponseDto rt = usuarios.crear(t);
        assertThat(rt.getCodti()).isEqualTo(2);
    }

    @Test
    @DisplayName("cambiarle la tienda a un empleado no hace nada; si el encargado cambia de rol o se desactiva, su tienda queda sin encargado")
    void actualizarEmpleado() {
        zocalo.setIdEncargado(10);
        ana.setTienda(zocalo);
        when(tiendaRepository.findFirstByIdEncargado(10)).thenReturn(Optional.of(zocalo));
        when(empleadoPerfilRepository.findByUsuarioIdusuario(any())).thenReturn(Optional.empty());

        UsuarioUpdateDto cambiaTienda = new UsuarioUpdateDto();
        cambiaTienda.setCodti(2);
        usuarios.actualizar(10, cambiaTienda);
        assertThat(ana.getTienda()).isSameAs(zocalo);          // se ignoró

        UsuarioUpdateDto cambiaRol = new UsuarioUpdateDto();
        cambiaRol.setRol("VENDEDOR");
        usuarios.actualizar(10, cambiaRol);
        assertThat(zocalo.getIdEncargado()).isNull();
        assertThat(ana.getTienda()).isNull();
        assertThat(ana.getRol()).isEqualTo(Rol.VENDEDOR);
    }
}
