package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.MovimientoInventarioRepository;
import com.skycel.backend.repository.ProductoImeiRepository;
import com.skycel.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Un ajuste o salida manual hecha por un encargado avisa a los administradores; lo hecho por un administrador o una venta, no. */
@ExtendWith(MockitoExtension.class)
class MovimientoInventarioAvisoTest {

    @Mock private MovimientoInventarioRepository repository;
    @Mock private ProductoImeiRepository imeiRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacionService notificacionService;

    private MovimientoInventarioService service;
    private Producto producto;

    @BeforeEach
    void setUp() {
        service = new MovimientoInventarioService(repository, imeiRepository, usuarioRepository, notificacionService);
        Tienda zocalo = Tienda.builder().codti(2).nombre("Zocalo").build();
        lenient().when(usuarioRepository.findByUsername("luis")).thenReturn(Optional.of(
                Usuario.builder().idusuario(4).username("luis").nombreCompleto("Luis").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build()));
        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(
                Usuario.builder().idusuario(1).username("admin").nombreCompleto("Admin").rol(Rol.ADMIN).tienda(zocalo).build()));
        producto = Producto.builder().codpro("MIC-1").tienda(zocalo).stock(new BigDecimal("7"))
                .productoMaster(ProductoMaster.builder().nombreBase("Mica Cristal").build()).build();
    }

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    private void como(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }

    @Test
    @DisplayName("una salida manual de un encargado avisa a los administradores con quién, qué, cuánto y por qué")
    void encargadoAvisa() {
        como("luis");

        service.registrar(producto, new BigDecimal("10"), "SALIDA", "Robo - se llevaron 3", null);

        ArgumentCaptor<String> titulo = ArgumentCaptor.forClass(String.class);
        verify(notificacionService).notificarRol(eq(Rol.ADMIN), eq("AJUSTE_INVENTARIO"), titulo.capture(), eq("#/mermas"));
        assertThat(titulo.getValue()).contains("Luis").contains("Zocalo").contains("sacó 3").contains("Mica Cristal").contains("Robo");
    }

    @Test
    @DisplayName("lo que hace un administrador no avisa")
    void adminNoAvisa() {
        como("admin");

        service.registrar(producto, new BigDecimal("10"), "AJUSTE", "Ajuste de inventario", null);

        verify(notificacionService, never()).notificarRol(any(), any(), any(), any());
    }

    @Test
    @DisplayName("una venta o un traspaso no avisan aunque los haga un encargado")
    void ventasNoAvisan() {
        como("luis");

        service.registrar(producto, new BigDecimal("10"), "VENTA", null, "Venta #1");
        service.registrar(producto, new BigDecimal("10"), "TRASPASO_SALIDA", null, "Traspaso #1");

        verify(notificacionService, never()).notificarRol(any(), any(), any(), any());
    }
}
