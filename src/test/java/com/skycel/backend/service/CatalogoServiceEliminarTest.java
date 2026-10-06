package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Color;
import com.skycel.backend.domain.entity.Proveedor;
import com.skycel.backend.domain.mapper.CatalogoMapper;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Eliminar colores y proveedores que ya no sirven: solo si nada activo los usa; la baja es lógica. */
@ExtendWith(MockitoExtension.class)
class CatalogoServiceEliminarTest {

    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private MagnitudRepository magnitudRepository;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private SeccionRepository seccionRepository;
    @Mock private TiendaRepository tiendaRepository;
    @Mock private CatalogoMapper catalogoMapper;
    @Mock private CategoriaService categoriaService;
    @Mock private ProductoRepository productoRepository;
    @Mock private ProductoMasterRepository productoMasterRepository;
    @InjectMocks private CatalogoService service;

    private Color color() {
        Color c = new Color();
        c.setIdcolor((short) 5);
        c.setNombre("Verde");
        c.setActivo(true);
        return c;
    }

    private Proveedor proveedor() {
        Proveedor p = new Proveedor();
        p.setIdproveedor((short) 2);
        p.setNombreCorto("Mayorista Movil MX");
        p.setActivo(true);
        return p;
    }

    @Test
    void elimina_un_color_que_nadie_usa() {
        Color c = color();
        when(colorRepository.findById((short) 5)).thenReturn(Optional.of(c));
        when(productoRepository.countByColor_IdcolorAndActivoTrue((short) 5)).thenReturn(0L);
        when(productoMasterRepository.countByColor_IdcolorAndActivoTrue((short) 5)).thenReturn(0L);

        service.eliminarColor((short) 5);

        assertThat(c.getActivo()).isFalse();
        verify(colorRepository).save(c);
    }

    @Test
    void no_elimina_un_color_en_uso_y_suma_productos_y_articulos() {
        Color c = color();
        when(colorRepository.findById((short) 5)).thenReturn(Optional.of(c));
        when(productoRepository.countByColor_IdcolorAndActivoTrue((short) 5)).thenReturn(7L);
        when(productoMasterRepository.countByColor_IdcolorAndActivoTrue((short) 5)).thenReturn(3L);

        assertThatThrownBy(() -> service.eliminarColor((short) 5))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("10 producto");
                });
        assertThat(c.getActivo()).isTrue();
    }

    @Test
    void elimina_un_proveedor_que_ningun_producto_activo_usa() {
        Proveedor p = proveedor();
        when(proveedorRepository.findById((short) 2)).thenReturn(Optional.of(p));
        when(productoRepository.countByProveedor_IdproveedorAndActivoTrue((short) 2)).thenReturn(0L);

        service.eliminarProveedor((short) 2);

        assertThat(p.getActivo()).isFalse();
        verify(proveedorRepository).save(p);
    }

    @Test
    void no_elimina_un_proveedor_con_productos_activos() {
        Proveedor p = proveedor();
        when(proveedorRepository.findById((short) 2)).thenReturn(Optional.of(p));
        when(productoRepository.countByProveedor_IdproveedorAndActivoTrue((short) 2)).thenReturn(40L);

        assertThatThrownBy(() -> service.eliminarProveedor((short) 2))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("40 producto");
                });
        assertThat(p.getActivo()).isTrue();
    }
}
