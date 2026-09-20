package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.MovimientoInventarioRepository;
import com.skycel.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Historial de cambios de stock (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class MovimientoInventarioServiceTest {

    @Mock private MovimientoInventarioRepository repository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private MovimientoInventarioService service;

    private Producto producto(String stock) {
        return Producto.builder().idproducto(1).codpro("ACC-000001").stock(new BigDecimal(stock)).build();
    }

    @Test
    @DisplayName("registra la diferencia con signo, y el stock antes y después")
    void registraLaDiferencia() {
        service.registrar(producto("7"), new BigDecimal("10"), "VENTA", "  ", "Venta #5");

        ArgumentCaptor<MovimientoInventario> cap = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(repository).save(cap.capture());
        MovimientoInventario m = cap.getValue();
        assertThat(m.getCantidad()).isEqualByComparingTo("-3");
        assertThat(m.getStockAntes()).isEqualByComparingTo("10");
        assertThat(m.getStockDespues()).isEqualByComparingTo("7");
        assertThat(m.getMotivo()).isNull();
        assertThat(m.getReferencia()).isEqualTo("Venta #5");
    }

    @Test
    @DisplayName("si el stock no cambió no registra nada")
    void sinCambioNoRegistra() {
        service.registrar(producto("10"), new BigDecimal("10"), "AJUSTE", "igual", null);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("un encargado no puede consultar el historial de otra tienda: 403; un admin sí")
    void permisos() {
        Tienda zocalo = Tienda.builder().codti(2).build();
        Usuario enc = Usuario.builder().username("abigail").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        Usuario root = Usuario.builder().username("root").rol(Rol.ROOT).build();
        when(usuarioRepository.findByUsername("abigail")).thenReturn(Optional.of(enc));
        when(usuarioRepository.findByUsername("root")).thenReturn(Optional.of(root));
        when(repository.buscar(anyInt(), any(), any(), isNull())).thenReturn(List.of());

        assertThatThrownBy(() -> service.listar(3, null, null, null, "abigail"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service.listar(2, null, null, null, "abigail")).isEmpty();
        assertThat(service.listar(3, null, null, "", "root")).isEmpty();
    }
}
