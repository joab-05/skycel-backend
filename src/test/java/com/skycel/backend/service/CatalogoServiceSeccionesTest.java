package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Seccion;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.mapper.CatalogoMapper;
import com.skycel.backend.repository.ProductoImeiRepository;
import com.skycel.backend.repository.ProductoRepository;
import com.skycel.backend.repository.SeccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Renombrar una sección y pasar todo lo que tiene a otra. */
@ExtendWith(MockitoExtension.class)
class CatalogoServiceSeccionesTest {

    @Mock private SeccionRepository seccionRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private ProductoImeiRepository productoImeiRepository;
    @Mock private CatalogoMapper catalogoMapper;
    @InjectMocks private CatalogoService service;

    private Seccion vitrina, mostrador, deOtraTienda;

    @BeforeEach
    void setUp() {
        Tienda zocalo = Tienda.builder().codti(2).nombre("Zócalo").build();
        Tienda corpo = Tienda.builder().codti(1).nombre("Corpo").build();
        vitrina = Seccion.builder().idseccion((short) 4).nombre("Vitrina").tienda(zocalo).activo(true).build();
        mostrador = Seccion.builder().idseccion((short) 5).nombre("Mostrador").tienda(zocalo).activo(true).build();
        deOtraTienda = Seccion.builder().idseccion((short) 6).nombre("Bodega").tienda(corpo).activo(true).build();
        lenient().when(seccionRepository.findById((short) 4)).thenReturn(Optional.of(vitrina));
        lenient().when(seccionRepository.findById((short) 5)).thenReturn(Optional.of(mostrador));
        lenient().when(seccionRepository.findById((short) 6)).thenReturn(Optional.of(deOtraTienda));
        lenient().when(seccionRepository.findByActivoTrue()).thenReturn(List.of(vitrina, mostrador, deOtraTienda));
        lenient().when(seccionRepository.save(any(Seccion.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("renombrar: cambia el nombre")
    void renombrar() {
        service.renombrarSeccion((short) 4, "  Vitrina principal ");

        assertThat(vitrina.getNombre()).isEqualTo("Vitrina principal");
    }

    @Test
    @DisplayName("renombrar: un nombre repetido en la misma sucursal es 409; en otra sucursal sí se puede")
    void renombrarRepetido() {
        assertThatThrownBy(() -> service.renombrarSeccion((short) 4, "mostrador"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Ya hay una sección");

        service.renombrarSeccion((short) 4, "Bodega");
        assertThat(vitrina.getNombre()).isEqualTo("Bodega");
    }

    @Test
    @DisplayName("renombrar: sin nombre, 400")
    void renombrarVacio() {
        assertThatThrownBy(() -> service.renombrarSeccion((short) 4, " "))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nombre");
    }

    @Test
    @DisplayName("mover: pasa los accesorios y los equipos a la otra sección y dice cuántos")
    void mover() {
        when(productoRepository.moverDeSeccion((short) 4, mostrador)).thenReturn(12);
        when(productoImeiRepository.moverDeSeccion((short) 4, mostrador)).thenReturn(3);

        Map<String, Object> r = service.moverSeccion((short) 4, (short) 5);

        assertThat(r.get("productos")).isEqualTo(12);
        assertThat(r.get("equipos")).isEqualTo(3);
        assertThat(r.get("destino")).isEqualTo("Mostrador");
    }

    @Test
    @DisplayName("mover: a la misma sección o a una de otra sucursal, 400")
    void moverInvalido() {
        assertThatThrownBy(() -> service.moverSeccion((short) 4, (short) 4)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.moverSeccion((short) 4, (short) 6)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("misma sucursal");
        verifyNoInteractions(productoImeiRepository);
    }
}
