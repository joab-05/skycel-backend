package com.skycel.backend.service;

import com.skycel.backend.domain.dto.response.DescripcionResponseDTO;
import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.DescripcionProducto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.repository.CategoriaRepository;
import com.skycel.backend.repository.DescripcionProductoRepository;
import com.skycel.backend.repository.ProductoMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El catálogo de descripciones: se reutilizan en lugar de repetirse, aunque cambien mayúsculas, acentos o espacios. */
@ExtendWith(MockitoExtension.class)
class DescripcionServiceTest {

    @Mock private DescripcionProductoRepository descripcionRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ProductoMasterRepository productoMasterRepository;
    @InjectMocks private DescripcionService service;

    private Categoria equipos;
    private Categoria celular;
    private Categoria samsung;

    @BeforeEach
    void setUp() {
        equipos = Categoria.builder().idcat((short) 1).nombre("Equipos").tipo(TipoProducto.CELULAR).incluirEnNombre(false).activo(true).build();
        celular = Categoria.builder().idcat((short) 2).nombre("Celular").tipo(TipoProducto.CELULAR).categoriaSuperior(equipos).incluirEnNombre(true).activo(true).build();
        samsung = Categoria.builder().idcat((short) 3).nombre("Samsung").tipo(TipoProducto.CELULAR).categoriaSuperior(celular).incluirEnNombre(true).activo(true).build();
        lenient().when(descripcionRepository.save(any(DescripcionProducto.class))).thenAnswer(i -> {
            DescripcionProducto d = i.getArgument(0);
            if (d.getIddescripcion() == null) d.setIddescripcion(100);
            return d;
        });
    }

    private DescripcionProducto existente(int id, String nombre) {
        return DescripcionProducto.builder().iddescripcion(id).categoria(samsung).nombre(nombre).activo(true).build();
    }

    @Test
    void crea_la_descripcion_si_no_existe_limpiando_espacios() {
        when(descripcionRepository.findByCategoria_Idcat((short) 3)).thenReturn(List.of());
        DescripcionProducto d = service.obtenerOCrear(samsung, "  A16   4/128gb ");
        assertThat(d.getNombre()).isEqualTo("A16 4/128gb");
        assertThat(d.getCategoria()).isSameAs(samsung);
        verify(descripcionRepository).save(any(DescripcionProducto.class));
    }

    @Test
    void reutiliza_la_existente_aunque_cambien_mayusculas_acentos_o_espacios() {
        DescripcionProducto ya = existente(7, "Cámara Frontal A16");
        when(descripcionRepository.findByCategoria_Idcat((short) 3)).thenReturn(List.of(ya));
        assertThat(service.obtenerOCrear(samsung, "camara  frontal a16")).isSameAs(ya);
        verify(descripcionRepository, never()).save(any(DescripcionProducto.class));
    }

    @Test
    void reactiva_una_descripcion_desactivada_en_vez_de_crear_otra() {
        DescripcionProducto ya = existente(7, "A16");
        ya.setActivo(false);
        when(descripcionRepository.findByCategoria_Idcat((short) 3)).thenReturn(List.of(ya));
        assertThat(service.obtenerOCrear(samsung, "a16").getActivo()).isTrue();
    }

    @Test
    void no_acepta_vacia_ni_colgada_de_la_categoria_principal() {
        assertThatThrownBy(() -> service.obtenerOCrear(samsung, "   "))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.obtenerOCrear(equipos, "A16"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void obtenerDe_exige_que_sea_de_la_misma_categoria() {
        DescripcionProducto deCelular = DescripcionProducto.builder().iddescripcion(5).categoria(celular).nombre("X").activo(true).build();
        when(descripcionRepository.findById(5)).thenReturn(java.util.Optional.of(deCelular));
        assertThatThrownBy(() -> service.obtenerDe(5, samsung))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason()).contains("otra categoría"));
    }

    @Test
    void renombrar_actualiza_los_articulos_armados_y_respeta_los_de_nombre_propio() {
        DescripcionProducto d = existente(7, "A16");
        when(descripcionRepository.findById(7)).thenReturn(java.util.Optional.of(d));
        when(descripcionRepository.findByCategoria_Idcat((short) 3)).thenReturn(List.of(d));
        ProductoMaster armado = ProductoMaster.builder().idprodmaster(1).categoria(samsung).descripcion(d)
                .nombreProducto("A16").nombreBase("Celular Samsung A16").build();
        ProductoMaster propio = ProductoMaster.builder().idprodmaster(2).categoria(samsung).descripcion(d)
                .nombreProducto("A16").nombreBase("Mi nombre propio").build();
        when(productoMasterRepository.findByDescripcion_Iddescripcion(7)).thenReturn(List.of(armado, propio));

        DescripcionResponseDTO r = service.renombrar(7, "A16 5G");

        assertThat(r.getNombre()).isEqualTo("A16 5G");
        assertThat(armado.getNombreBase()).isEqualTo("Celular Samsung A16 5G");
        assertThat(propio.getNombreBase()).isEqualTo("Mi nombre propio");
        assertThat(propio.getNombreProducto()).isEqualTo("A16 5G");
    }

    @Test
    void elimina_de_forma_logica_una_descripcion_que_nadie_usa() {
        DescripcionProducto d = existente(7, "A16");
        when(descripcionRepository.findById(7)).thenReturn(java.util.Optional.of(d));
        when(productoMasterRepository.countByDescripcion_IddescripcionAndActivoTrue(7)).thenReturn(0L);
        service.eliminar(7);
        assertThat(d.getActivo()).isFalse();
        verify(descripcionRepository).save(d);
    }

    @Test
    void no_elimina_una_descripcion_que_usan_articulos_activos() {
        DescripcionProducto d = existente(7, "A16");
        when(descripcionRepository.findById(7)).thenReturn(java.util.Optional.of(d));
        when(productoMasterRepository.countByDescripcion_IddescripcionAndActivoTrue(7)).thenReturn(5L);
        assertThatThrownBy(() -> service.eliminar(7))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("5 artículo");
                });
        assertThat(d.getActivo()).isTrue();
    }

    @Test
    void desactivarSinUso_solo_toca_las_descripciones_que_ningun_articulo_usa() {
        DescripcionProducto libre = existente(7, "A16");
        DescripcionProducto usada = existente(8, "A17");
        when(descripcionRepository.findByCategoria_IdcatAndActivoTrueOrderByNombreAsc((short) 3)).thenReturn(List.of(libre, usada));
        when(productoMasterRepository.findByDescripcion_Iddescripcion(7)).thenReturn(List.of());
        when(productoMasterRepository.findByDescripcion_Iddescripcion(8)).thenReturn(List.of(ProductoMaster.builder().idprodmaster(1).build()));

        assertThat(service.desactivarSinUso((short) 3)).isEqualTo(1);
        assertThat(libre.getActivo()).isFalse();
        assertThat(usada.getActivo()).isTrue();
    }

    @Test
    void renombrar_no_deja_repetir_otra_descripcion_de_la_categoria() {
        DescripcionProducto d = existente(7, "A16");
        DescripcionProducto otra = existente(8, "A17");
        when(descripcionRepository.findById(7)).thenReturn(java.util.Optional.of(d));
        when(descripcionRepository.findByCategoria_Idcat((short) 3)).thenReturn(List.of(d, otra));
        assertThatThrownBy(() -> service.renombrar(7, "a17"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
