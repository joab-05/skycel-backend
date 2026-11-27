package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.producto.MigracionRefaccionesDto;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Migración de las refacciones del taller al tipo Refacción")
class MigracionRefaccionesServiceTest {

    private final TiendaRepository tiendaRepo = mock(TiendaRepository.class);
    private final ProductoRepository productoRepo = mock(ProductoRepository.class);
    private final ProductoMasterRepository masterRepo = mock(ProductoMasterRepository.class);
    private final CategoriaRepository categoriaRepo = mock(CategoriaRepository.class);
    private final CategoriaFolioRepository folioRepo = mock(CategoriaFolioRepository.class);
    private final DescripcionService descripcionService = mock(DescripcionService.class);
    private final MigracionRefaccionesService service = new MigracionRefaccionesService(tiendaRepo, productoRepo, masterRepo, categoriaRepo, folioRepo, descripcionService);

    private final Tienda taller = Tienda.builder().codti(7).nombre("Taller Superche").esTaller(true).build();
    private final Categoria accesorios = cat(56, "Accesorios", null, null, null);
    private final Categoria refacciones = cat(387, "Refacciones", "REF", accesorios, null);
    private final Categoria pantalla = cat(27, "Pantalla", "PAN", accesorios, null);
    private final Categoria tapa = cat(33, "Tapa", "TAP", accesorios, null);
    private final Categoria samsung = cat(343, "Samsung", null, tapa, null);
    private final DescripcionProducto descAntes = DescripcionProducto.builder().iddescripcion(1).categoria(pantalla).nombre("Pantalla original").activo(true).build();
    private final DescripcionProducto descDespues = DescripcionProducto.builder().iddescripcion(2).nombre("Pantalla original").activo(true).build();

    private final ProductoMaster mGeneral = master(1, "Pantalla iPhone 11", TipoProducto.ACCESORIO, refacciones);
    private final ProductoMaster mPantalla = master(2, "Pantalla Samsung A16", TipoProducto.ACCESORIO, pantalla);
    private final ProductoMaster mTapa = master(3, "Tapa Samsung A52", TipoProducto.ACCESORIO, samsung);
    private final ProductoMaster mServicio = master(4, "Cambio de pantalla", TipoProducto.SERVICIO, pantalla);
    private final ProductoMaster mCompartido = master(5, "Cargador universal", TipoProducto.ACCESORIO, pantalla);
    private final AtomicInteger ids = new AtomicInteger(100);

    private static Categoria cat(int id, String nombre, String codigo, Categoria padre, TipoProducto tipo) {
        return Categoria.builder().idcat((short) id).nombre(nombre).codigo(codigo).tipo(tipo != null ? tipo : TipoProducto.ACCESORIO)
                .categoriaSuperior(padre).activo(true).incluirEnNombre(padre != null).build();
    }

    private static ProductoMaster master(int id, String nombre, TipoProducto tipo, Categoria categoria) {
        return ProductoMaster.builder().idprodmaster(id).nombreBase(nombre).tipo(tipo).categoria(categoria).activo(true).build();
    }

    private Producto producto(int id, ProductoMaster m, String stock) {
        return Producto.builder().idproducto(id).codpro("X-" + id).tienda(taller).productoMaster(m).stock(new BigDecimal(stock)).activo(true).build();
    }

    @BeforeEach
    void preparar() {
        mPantalla.setDescripcion(descAntes);
        when(tiendaRepo.findByEsTallerTrue()).thenReturn(List.of(taller));
        when(productoRepo.findByTienda_Codti(7)).thenReturn(List.of(producto(1, mGeneral, "5"), producto(2, mPantalla, "0"), producto(3, mTapa, "2"),
                producto(4, mServicio, "0"), producto(5, mCompartido, "9")));
        when(productoRepo.existsByProductoMaster_IdprodmasterAndTienda_CodtiNotIn(eq(5), anyCollection())).thenReturn(true);
        when(categoriaRepo.findByNombreIgnoreCaseAndCategoriaSuperiorIsNull("Refacciones")).thenReturn(Optional.empty());
        when(categoriaRepo.findByNombreIgnoreCaseAndCategoriaSuperiorIdcat(any(), any())).thenReturn(Optional.empty());
        when(categoriaRepo.save(any(Categoria.class))).thenAnswer(i -> {
            Categoria c = i.getArgument(0);
            if (c.getIdcat() == null) c.setIdcat((short) ids.incrementAndGet());
            return c;
        });
        when(categoriaRepo.saveAndFlush(any(Categoria.class))).thenAnswer(i -> i.getArgument(0));
        when(categoriaRepo.findIdsByCategoriaSuperiorId(anyShort())).thenReturn(List.of());
        when(categoriaRepo.findIdsByCategoriaSuperiorId((short) 33)).thenReturn(List.of((short) 343));
        when(categoriaRepo.findById((short) 343)).thenReturn(Optional.of(samsung));
        when(masterRepo.countByCategoria_Idcat(anyShort())).thenReturn(0L);
        when(masterRepo.countByCategoria_Idcat((short) 27)).thenReturn(1L);            // el artículo compartido sigue en «Pantalla»
        when(folioRepo.findById((short) 387)).thenReturn(Optional.of(CategoriaFolio.builder().idcat((short) 387).ultimoFolio(62L).build()));
        when(descripcionService.obtenerOCrear(any(Categoria.class), eq("Pantalla original"))).thenReturn(descDespues);
    }

    @Test
    @DisplayName("el plan cuenta lo que cambiaría (sin servicios ni lo compartido con otras tiendas) y no guarda nada")
    void plan() {
        MigracionRefaccionesDto r = service.planear();

        assertThat(r.aplicado()).isFalse();
        assertThat(r.articulos()).isEqualTo(3);
        assertThat(r.productos()).isEqualTo(3);
        assertThat(r.productosConExistencia()).isEqualTo(2);
        assertThat(r.omitidos()).containsExactly("Cargador universal");
        assertThat(r.raizNueva()).isTrue();
        assertThat(r.categoriasNuevas()).containsExactlyInAnyOrder("Refacciones > General", "Refacciones > Pantalla", "Refacciones > Tapa",
                "Refacciones > Tapa > Samsung");
        assertThat(r.ejemplos()).anyMatch(e -> e.contains("Accesorios > Refacciones") && e.contains("Refacciones > General"));
        verify(categoriaRepo, never()).save(any());
        verify(masterRepo, never()).save(any());
        assertThat(mGeneral.getTipo()).isEqualTo(TipoProducto.ACCESORIO);
    }

    @Test
    @DisplayName("al aplicar, los artículos pasan a Refacción bajo la nueva principal, con sus descripciones y el consecutivo de códigos")
    void aplica() {
        MigracionRefaccionesDto r = service.aplicar();

        assertThat(r.aplicado()).isTrue();
        assertThat(mGeneral.getTipo()).isEqualTo(TipoProducto.REFACCION);
        assertThat(mPantalla.getTipo()).isEqualTo(TipoProducto.REFACCION);
        assertThat(mTapa.getTipo()).isEqualTo(TipoProducto.REFACCION);
        assertThat(mServicio.getTipo()).isEqualTo(TipoProducto.SERVICIO);        // los servicios no cambian
        assertThat(mCompartido.getTipo()).isEqualTo(TipoProducto.ACCESORIO);      // lo compartido con otras tiendas tampoco

        Categoria raiz = mGeneral.getCategoria().getCategoriaSuperior();
        assertThat(raiz.getNombre()).isEqualTo("Refacciones");
        assertThat(raiz.getTipo()).isEqualTo(TipoProducto.REFACCION);
        assertThat(raiz.getCategoriaSuperior()).isNull();
        assertThat(mGeneral.getCategoria().getNombre()).isEqualTo("General");     // la antigua subcategoría «Refacciones»
        assertThat(mGeneral.getCategoria().getTipo()).isEqualTo(TipoProducto.REFACCION);
        assertThat(mPantalla.getCategoria().getNombre()).isEqualTo("Pantalla");
        assertThat(mPantalla.getCategoria().getCategoriaSuperior()).isSameAs(raiz);
        assertThat(mTapa.getCategoria().ruta()).extracting(Categoria::getNombre).containsExactly("Refacciones", "Tapa", "Samsung");
        assertThat(mPantalla.getDescripcion()).isSameAs(descDespues);

        // el consecutivo de códigos sigue donde iba
        verify(folioRepo).fijarMinimo(eq(mGeneral.getCategoria().getIdcat()), eq(62L));
    }

    @Test
    @DisplayName("las categorías de antes que quedan vacías se retiran y su código pasa a la nueva; si siguen en uso, la nueva lleva uno parecido")
    void categoriasYCodigos() {
        MigracionRefaccionesDto r = service.aplicar();

        assertThat(r.categoriasRetiradas()).contains("Accesorios > Refacciones", "Accesorios > Tapa > Samsung", "Accesorios > Tapa")
                .doesNotContain("Accesorios > Pantalla");
        assertThat(refacciones.getActivo()).isFalse();
        assertThat(tapa.getActivo()).isFalse();
        assertThat(pantalla.getActivo()).isTrue();
        // REF pasó de la de antes a «General»; TAP, a «Tapa» nueva; PAN sigue en la de antes, así que la nueva lleva RPAN
        assertThat(refacciones.getCodigo()).isNull();
        assertThat(mGeneral.getCategoria().getCodigo()).isEqualTo("REF");
        assertThat(tapa.getCodigo()).isNull();
        assertThat(mTapa.getCategoria().getCategoriaSuperior().getCodigo()).isEqualTo("TAP");
        assertThat(pantalla.getCodigo()).isEqualTo("PAN");
        assertThat(mPantalla.getCategoria().getCodigo()).isEqualTo("RPAN");
    }

    @Test
    @DisplayName("si la principal «Refacciones» ya existe se reutiliza y no se duplican categorías; yaSeHizo lo indica")
    void repetir() {
        Categoria raizExistente = Categoria.builder().idcat((short) 88).nombre("Refacciones").tipo(TipoProducto.REFACCION).activo(true).build();
        Categoria generalExistente = Categoria.builder().idcat((short) 89).nombre("General").tipo(TipoProducto.REFACCION).categoriaSuperior(raizExistente).activo(true).build();
        when(categoriaRepo.findByNombreIgnoreCaseAndCategoriaSuperiorIsNull("Refacciones")).thenReturn(Optional.of(raizExistente));
        when(categoriaRepo.findByNombreIgnoreCaseAndCategoriaSuperiorIdcat("General", (short) 88)).thenReturn(Optional.of(generalExistente));

        assertThat(service.yaSeHizo()).isTrue();
        MigracionRefaccionesDto r = service.aplicar();

        assertThat(r.raizNueva()).isFalse();
        assertThat(mGeneral.getCategoria()).isSameAs(generalExistente);
        assertThat(r.categoriasNuevas()).doesNotContain("Refacciones > General");
    }

    @Test
    @DisplayName("sin tienda taller no hace nada; con una «Refacciones» que no es de ese tipo, avisa")
    void errores() {
        when(tiendaRepo.findByEsTallerTrue()).thenReturn(List.of());
        assertThatThrownBy(service::planear).isInstanceOf(ResponseStatusException.class).hasMessageContaining("taller");

        when(tiendaRepo.findByEsTallerTrue()).thenReturn(List.of(taller));
        when(categoriaRepo.findByNombreIgnoreCaseAndCategoriaSuperiorIsNull("Refacciones"))
                .thenReturn(Optional.of(Categoria.builder().idcat((short) 70).nombre("Refacciones").tipo(TipoProducto.ACCESORIO).activo(true).build()));
        assertThatThrownBy(service::aplicar).isInstanceOf(ResponseStatusException.class).hasMessageContaining("no es de tipo Refacción");
        assertThat(service.yaSeHizo()).isFalse();
    }
}
