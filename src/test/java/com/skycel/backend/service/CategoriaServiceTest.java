package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.repository.CategoriaRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ProductoMasterRepository productoMasterRepository;
    @Mock private DescripcionService descripcionService;
    @InjectMocks private CategoriaService service;

    private Categoria equipos;
    private Categoria celular;
    private Categoria samsung;

    @BeforeEach
    void setUp() {
        equipos = Categoria.builder().idcat((short) 1).nombre("Equipos").tipo(TipoProducto.CELULAR).incluirEnNombre(false).activo(true).build();
        celular = Categoria.builder().idcat((short) 2).nombre("Celular").codigo("CEL").tipo(TipoProducto.CELULAR)
                .categoriaSuperior(equipos).incluirEnNombre(true).activo(true).build();
        samsung = Categoria.builder().idcat((short) 3).nombre("Samsung").tipo(TipoProducto.CELULAR)
                .categoriaSuperior(celular).incluirEnNombre(true).activo(true).build();
        lenient().when(categoriaRepository.findById((short) 1)).thenReturn(Optional.of(equipos));
        lenient().when(categoriaRepository.findById((short) 2)).thenReturn(Optional.of(celular));
        lenient().when(categoriaRepository.findById((short) 3)).thenReturn(Optional.of(samsung));
        lenient().when(categoriaRepository.save(any(Categoria.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CategoriaRequestDTO dto(String nombre, String tipo, Short padre) {
        CategoriaRequestDTO d = new CategoriaRequestDTO();
        d.setNombreCat(nombre);
        d.setTipo(tipo);
        d.setIdCategoriaSuperior(padre);
        return d;
    }

    @Test
    void la_subcategoria_hereda_el_tipo_de_la_principal_e_incluye_su_nombre_por_defecto() {
        CategoriaResponseDTO r = service.crear(dto("Tablet", "ACCESORIO", (short) 1));
        assertThat(r.getTipo()).isEqualTo("CELULAR");
        assertThat(r.getIncluirEnNombre()).isTrue();
        assertThat(r.getNivel()).isEqualTo(2);
    }

    @Test
    void la_principal_no_incluye_su_nombre_por_defecto_y_exige_tipo() {
        assertThat(service.crear(dto("Accesorios", "ACCESORIO", null)).getIncluirEnNombre()).isFalse();
        assertThatThrownBy(() -> service.crear(dto("Otra", null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void no_permite_un_cuarto_nivel() {
        assertThatThrownBy(() -> service.crear(dto("A16", null, (short) 3)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("3 niveles");
                });
    }

    @Test
    void no_permite_repetir_el_codigo_corto() {
        when(categoriaRepository.existsByCodigoIgnoreCase("CEL")).thenReturn(true);
        CategoriaRequestDTO d = dto("Celulares 2", null, (short) 1);
        d.setCodigo("cel");
        assertThatThrownBy(() -> service.crear(d))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void renombrar_una_categoria_rearma_el_nombre_de_los_articulos_armados_y_respeta_los_manuales() {
        ProductoMaster armado = ProductoMaster.builder().idprodmaster(10).categoria(samsung)
                .nombreProducto("A16 Verde").nombreBase("Celular Samsung A16 Verde").build();
        ProductoMaster manual = ProductoMaster.builder().idprodmaster(11).categoria(samsung)
                .nombreProducto("S24").nombreBase("Galaxy S24 Ultra (nombre propio)").build();
        when(categoriaRepository.findIdsByCategoriaSuperiorId((short) 3)).thenReturn(List.of());
        when(productoMasterRepository.findByCategoria_IdcatIn(List.of((short) 3))).thenReturn(List.of(armado, manual));

        service.actualizar((short) 3, dto("Samsung Galaxy", null, (short) 2));

        assertThat(armado.getNombreBase()).isEqualTo("Celular Samsung Galaxy A16 Verde");
        assertThat(manual.getNombreBase()).isEqualTo("Galaxy S24 Ultra (nombre propio)");
    }

    @Test
    void elimina_de_forma_logica_una_categoria_sin_hijas_ni_articulos_y_limpia_sus_descripciones() {
        when(categoriaRepository.countByCategoriaSuperiorIdcatAndActivoTrue((short) 3)).thenReturn(0L);
        when(productoMasterRepository.countByCategoria_IdcatAndActivoTrue((short) 3)).thenReturn(0L);

        service.eliminar((short) 3);

        assertThat(samsung.getActivo()).isFalse();
        verify(categoriaRepository).save(samsung);
        verify(descripcionService).desactivarSinUso((short) 3);
    }

    @Test
    void no_elimina_una_categoria_con_subcategorias_activas() {
        when(categoriaRepository.countByCategoriaSuperiorIdcatAndActivoTrue((short) 2)).thenReturn(4L);
        assertThatThrownBy(() -> service.eliminar((short) 2))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("4 subcategoría");
                });
        assertThat(celular.getActivo()).isTrue();
    }

    @Test
    void no_elimina_una_categoria_que_usan_articulos_activos_y_dice_cuantos() {
        when(categoriaRepository.countByCategoriaSuperiorIdcatAndActivoTrue((short) 3)).thenReturn(0L);
        when(productoMasterRepository.countByCategoria_IdcatAndActivoTrue((short) 3)).thenReturn(12L);
        assertThatThrownBy(() -> service.eliminar((short) 3))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("12 artículo");
                });
        assertThat(samsung.getActivo()).isTrue();
    }

    @Test
    void crear_con_el_nombre_de_una_categoria_eliminada_la_reactiva_en_vez_de_duplicarla() {
        Categoria eliminada = Categoria.builder().idcat((short) 9).nombre("Tablet").codigo("TAB").tipo(TipoProducto.CELULAR)
                .categoriaSuperior(equipos).incluirEnNombre(true).activo(false).build();
        when(categoriaRepository.findByNombreIgnoreCaseAndCategoriaSuperiorIdcat("Tablet", (short) 1)).thenReturn(Optional.of(eliminada));

        CategoriaResponseDTO r = service.crear(dto("Tablet", null, (short) 1));

        assertThat(r.getIdcat()).isEqualTo((short) 9);
        assertThat(eliminada.getActivo()).isTrue();
    }

    @Test
    void crear_con_el_nombre_de_una_categoria_vigente_sigue_siendo_conflicto() {
        when(categoriaRepository.findByNombreIgnoreCaseAndCategoriaSuperiorIdcat("Celular", (short) 1)).thenReturn(Optional.of(celular));
        assertThatThrownBy(() -> service.crear(dto("Celular", null, (short) 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void no_permite_colgar_una_categoria_de_su_propia_subcategoria() {
        when(categoriaRepository.findIdsByCategoriaSuperiorId((short) 1)).thenReturn(List.of((short) 2));
        when(categoriaRepository.findIdsByCategoriaSuperiorId((short) 2)).thenReturn(List.of());
        assertThatThrownBy(() -> service.actualizar((short) 1, dto("Equipos", null, (short) 2)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
