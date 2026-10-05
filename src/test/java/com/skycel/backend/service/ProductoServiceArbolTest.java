package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ProductoMasterUpdateDTO;
import com.skycel.backend.domain.dto.request.ProductoRequestDTO;
import com.skycel.backend.domain.dto.response.ProductoMasterResponseDTO;
import com.skycel.backend.domain.dto.response.ProductoResponseDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.domain.mapper.ProductoMapper;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.*;

/** Registro de artículos por árbol de categorías: nombre armado, tipo heredado y código por subcategoría 1. */
@ExtendWith(MockitoExtension.class)
class ProductoServiceArbolTest {

    @Mock private ProductoMasterRepository productoMasterRepository;
    @Mock private ProductoRepository       productoRepository;
    @Mock private CategoriaRepository      categoriaRepository;
    @Mock private TiendaRepository         tiendaRepository;
    @Mock private ColorRepository          colorRepository;
    @Mock private MagnitudRepository       magnitudRepository;
    @Mock private ProveedorRepository      proveedorRepository;
    @Mock private SeccionRepository        seccionRepository;
    @Mock private ProductoImeiRepository   productoImeiRepository;
    @Mock private CategoriaFolioRepository categoriaFolioRepository;
    @Mock private ProductoMapper           productoMapper;
    @Mock private MovimientoInventarioService movimientoInventarioService;
    @Mock private DescuentoService         descuentoService;
    @Mock private DescripcionService       descripcionService;
    @InjectMocks private ProductoService service;

    private Categoria equipos, celular, samsung, accesorios, cargador, servicios, reparacion, pantalla;

    @BeforeEach
    void setUp() {
        equipos = cat(1, "Equipos", null, null, TipoProducto.CELULAR, false);
        celular = cat(2, "Celular", equipos, "CEL", TipoProducto.CELULAR, true);
        samsung = cat(3, "Samsung", celular, null, TipoProducto.CELULAR, true);
        accesorios = cat(4, "Accesorios", null, null, TipoProducto.ACCESORIO, false);
        cargador = cat(5, "Cargador", accesorios, "CAR", TipoProducto.ACCESORIO, true);
        servicios = cat(6, "Servicios", null, null, TipoProducto.SERVICIO, false);
        reparacion = cat(7, "Reparación", servicios, "REP", TipoProducto.SERVICIO, false);
        pantalla = cat(8, "Cambio de Pantalla", reparacion, null, TipoProducto.SERVICIO, true);
        for (Categoria c : new Categoria[]{equipos, celular, samsung, accesorios, cargador, servicios, reparacion, pantalla}) {
            lenient().when(categoriaRepository.findById(c.getIdcat())).thenReturn(Optional.of(c));
        }
        lenient().when(productoMasterRepository.save(any(ProductoMaster.class))).thenAnswer(i -> {
            ProductoMaster m = i.getArgument(0);
            if (m.getIdprodmaster() == null) m.setIdprodmaster(99);
            return m;
        });
        lenient().when(tiendaRepository.findById(1)).thenReturn(Optional.of(Tienda.builder().codti(1).nombre("Zócalo").build()));
        lenient().when(magnitudRepository.findById((short) 1)).thenReturn(Optional.of(new Magnitud()));
        lenient().when(productoMapper.toProductoEntity(any())).thenAnswer(i -> new Producto());
        lenient().when(productoMapper.toProductoResponse(any(Producto.class))).thenAnswer(i -> new ProductoResponseDTO());
        lenient().when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(categoriaFolioRepository.obtenerUltimoFolioGenerado(anyShort())).thenReturn(7L);
        // el catálogo de descripciones: cada texto se vuelve una descripción de la categoría
        lenient().when(descripcionService.obtenerOCrear(any(Categoria.class), anyString())).thenAnswer(i ->
                DescripcionProducto.builder().iddescripcion(1).categoria(i.getArgument(0)).nombre(((String) i.getArgument(1)).trim()).activo(true).build());
    }

    private Categoria cat(int id, String nombre, Categoria padre, String codigo, TipoProducto tipo, boolean incluir) {
        return Categoria.builder().idcat((short) id).nombre(nombre).codigo(codigo).tipo(tipo)
                .categoriaSuperior(padre).incluirEnNombre(incluir).activo(true).build();
    }

    private ProductoRequestDTO alta(Short idCategoria, String nombreProducto) {
        ProductoRequestDTO d = new ProductoRequestDTO();
        d.setCodti(1);
        d.setIdCategoria(idCategoria);
        d.setNombreProducto(nombreProducto);
        d.setStock(BigDecimal.ZERO);
        d.setPrecioCompra(BigDecimal.TEN);
        d.setPrecioVenta(BigDecimal.valueOf(20));
        return d;
    }

    @Test
    void equipo_arma_el_nombre_con_el_camino_toma_el_tipo_de_la_principal_y_el_codigo_de_la_subcategoria_1() {
        service.crearProducto(alta((short) 3, "A16 4/128gb Verde"));

        ArgumentCaptor<ProductoMaster> master = ArgumentCaptor.forClass(ProductoMaster.class);
        verify(productoMasterRepository).save(master.capture());
        assertThat(master.getValue().getNombreBase()).isEqualTo("Celular Samsung A16 4/128gb Verde");
        assertThat(master.getValue().getNombreProducto()).isEqualTo("A16 4/128gb Verde");
        assertThat(master.getValue().getTipo()).isEqualTo(TipoProducto.CELULAR);
        assertThat(master.getValue().getCategoria()).isSameAs(samsung);

        ArgumentCaptor<Producto> prod = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(prod.capture());
        assertThat(prod.getValue().getCodpro()).isEqualTo("CEL-000007");
        // el contador es el de la subcategoría 1 (Celular), no el de la marca
        verify(categoriaFolioRepository).incrementar((short) 2);
    }

    @Test
    void servicio_omite_la_subcategoria_1_en_el_nombre_pero_el_codigo_sale_de_ella() {
        service.crearProducto(alta((short) 8, "iPhone 13"));

        ArgumentCaptor<ProductoMaster> master = ArgumentCaptor.forClass(ProductoMaster.class);
        verify(productoMasterRepository).save(master.capture());
        assertThat(master.getValue().getNombreBase()).isEqualTo("Cambio de Pantalla iPhone 13");
        assertThat(master.getValue().getTipo()).isEqualTo(TipoProducto.SERVICIO);
        ArgumentCaptor<Producto> prod = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(prod.capture());
        assertThat(prod.getValue().getCodpro()).isEqualTo("REP-000007");
        assertThat(prod.getValue().getStock()).isEqualByComparingTo("0");
    }

    @Test
    void accesorio_sin_variante_es_el_ultimo_nivel_del_arbol() {
        service.crearProducto(alta((short) 5, null));
        ArgumentCaptor<ProductoMaster> master = ArgumentCaptor.forClass(ProductoMaster.class);
        verify(productoMasterRepository).save(master.capture());
        assertThat(master.getValue().getNombreBase()).isEqualTo("Cargador");
        assertThat(master.getValue().getNombreProducto()).isNull();
    }

    @Test
    void reutiliza_el_articulo_si_ya_existe_con_ese_nombre_completo() {
        ProductoMaster existente = ProductoMaster.builder().idprodmaster(5).tipo(TipoProducto.ACCESORIO)
                .categoria(cargador).nombreBase("Cargador Tipo C").build();
        when(productoMasterRepository.findByNombreBaseIgnoreCaseAndActivoTrue("Cargador Tipo C")).thenReturn(Optional.of(existente));

        service.crearProducto(alta((short) 5, "Tipo C"));

        verify(productoMasterRepository, never()).save(any(ProductoMaster.class));
        ArgumentCaptor<Producto> prod = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(prod.capture());
        assertThat(prod.getValue().getProductoMaster()).isSameAs(existente);
    }

    @Test
    void los_articulos_no_cuelgan_de_la_categoria_principal() {
        assertThatThrownBy(() -> service.crearProducto(alta((short) 1, "A16")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("subcategoría 1");
                });
        verify(productoMasterRepository, never()).save(any(ProductoMaster.class));
    }

    @Test
    void subcategoria_1_sin_codigo_no_deja_registrar_accesorios() {
        Categoria sinCodigo = cat(9, "Memorias", accesorios, null, TipoProducto.ACCESORIO, true);
        when(categoriaRepository.findById((short) 9)).thenReturn(Optional.of(sinCodigo));
        assertThatThrownBy(() -> service.crearProducto(alta((short) 9, "SD 64gb")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason()).contains("código corto"));
    }

    @Test
    void un_codigo_ya_usado_se_salta_y_toma_el_siguiente_folio() {
        when(categoriaFolioRepository.obtenerUltimoFolioGenerado((short) 2)).thenReturn(7L, 8L);
        when(productoRepository.findAllByCodpro("CEL-000007")).thenReturn(java.util.List.of(new Producto()));
        service.crearProducto(alta((short) 3, "A16"));
        ArgumentCaptor<Producto> prod = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(prod.capture());
        assertThat(prod.getValue().getCodpro()).isEqualTo("CEL-000008");
    }

    @Test
    void el_color_va_aparte_de_la_descripcion_y_al_final_del_nombre() {
        Color verde = new Color();
        verde.setIdcolor((short) 5);
        verde.setNombre("Verde");
        when(colorRepository.findById((short) 5)).thenReturn(Optional.of(verde));
        ProductoRequestDTO dto = alta((short) 3, "A16 4/128gb");
        dto.setIdColor((short) 5);

        service.crearProducto(dto);

        ArgumentCaptor<ProductoMaster> master = ArgumentCaptor.forClass(ProductoMaster.class);
        verify(productoMasterRepository).save(master.capture());
        assertThat(master.getValue().getNombreBase()).isEqualTo("Celular Samsung A16 4/128gb Verde");
        assertThat(master.getValue().getNombreProducto()).isEqualTo("A16 4/128gb");   // la descripción no lleva el color
        assertThat(master.getValue().getColor()).isSameAs(verde);
        ArgumentCaptor<Producto> prod = ArgumentCaptor.forClass(Producto.class);
        verify(productoRepository).save(prod.capture());
        assertThat(prod.getValue().getColor()).isSameAs(verde);   // el producto lleva el color de su artículo
    }

    @Test
    void la_misma_descripcion_con_otro_color_es_otro_articulo_que_reutiliza_la_descripcion() {
        Color azul = new Color();
        azul.setIdcolor((short) 3);
        azul.setNombre("Azul");
        when(colorRepository.findById((short) 3)).thenReturn(Optional.of(azul));
        ProductoRequestDTO dto = alta((short) 3, "A16 4/128gb");
        dto.setIdColor((short) 3);
        service.crearProducto(dto);

        ArgumentCaptor<ProductoMaster> master = ArgumentCaptor.forClass(ProductoMaster.class);
        verify(productoMasterRepository).save(master.capture());
        assertThat(master.getValue().getNombreBase()).isEqualTo("Celular Samsung A16 4/128gb Azul");
        verify(descripcionService).obtenerOCrear(samsung, "A16 4/128gb");   // una sola descripción en el catálogo
    }

    @Test
    void una_descripcion_elegida_del_catalogo_se_usa_tal_cual() {
        DescripcionProducto elegida = DescripcionProducto.builder().iddescripcion(9).categoria(samsung).nombre("A16 4/128gb").activo(true).build();
        when(descripcionService.obtenerDe(9, samsung)).thenReturn(elegida);
        ProductoRequestDTO dto = alta((short) 3, null);
        dto.setIdDescripcion(9);

        service.crearProducto(dto);

        ArgumentCaptor<ProductoMaster> master = ArgumentCaptor.forClass(ProductoMaster.class);
        verify(productoMasterRepository).save(master.capture());
        assertThat(master.getValue().getDescripcion()).isSameAs(elegida);
        assertThat(master.getValue().getNombreBase()).isEqualTo("Celular Samsung A16 4/128gb");
        verify(descripcionService, never()).obtenerOCrear(any(Categoria.class), anyString());
    }

    @Test
    void cambiar_el_color_rearma_el_nombre_y_el_color_de_los_productos() {
        Color rojo = new Color();
        rojo.setIdcolor((short) 4);
        rojo.setNombre("Rojo");
        when(colorRepository.findById((short) 4)).thenReturn(Optional.of(rojo));
        ProductoMaster m = ProductoMaster.builder().idprodmaster(7).tipo(TipoProducto.CELULAR).categoria(samsung)
                .nombreProducto("A16").nombreBase("Celular Samsung A16").build();
        Producto fila = Producto.builder().idproducto(1).productoMaster(m).build();
        when(productoMasterRepository.findById(7)).thenReturn(Optional.of(m));
        when(productoRepository.findByProductoMaster_IdprodmasterOrderByIdproductoAsc(7)).thenReturn(java.util.List.of(fila));
        when(productoMapper.toResponse(any(ProductoMaster.class))).thenAnswer(i -> new ProductoMasterResponseDTO());

        ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
        dto.setIdColor((short) 4);
        service.actualizarMaster(7, dto);

        assertThat(m.getNombreBase()).isEqualTo("Celular Samsung A16 Rojo");
        assertThat(m.getColor()).isSameAs(rojo);
        assertThat(fila.getColor()).isSameAs(rojo);
    }

    @Test
    void cambiar_la_variante_rearma_el_nombre_completo() {
        ProductoMaster m = ProductoMaster.builder().idprodmaster(7).tipo(TipoProducto.CELULAR).categoria(samsung)
                .nombreProducto("A16 Verde").nombreBase("Celular Samsung A16 Verde").build();
        when(productoMasterRepository.findById(7)).thenReturn(Optional.of(m));
        when(productoMapper.toResponse(any(ProductoMaster.class))).thenAnswer(i -> new ProductoMasterResponseDTO());

        ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
        dto.setNombreProducto("A16 Azul");
        service.actualizarMaster(7, dto);

        assertThat(m.getNombreBase()).isEqualTo("Celular Samsung A16 Azul");
        assertThat(m.getNombreProducto()).isEqualTo("A16 Azul");
    }

    @Test
    void un_nombre_propio_enviado_gana_sobre_el_armado() {
        ProductoMaster m = ProductoMaster.builder().idprodmaster(7).tipo(TipoProducto.CELULAR).categoria(samsung)
                .nombreProducto("A16 Verde").nombreBase("Celular Samsung A16 Verde").build();
        when(productoMasterRepository.findById(7)).thenReturn(Optional.of(m));
        when(productoMapper.toResponse(any(ProductoMaster.class))).thenAnswer(i -> new ProductoMasterResponseDTO());

        ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
        dto.setNombreProducto("A16 Azul");
        dto.setNombreBase("Samsung A16 Azul");
        service.actualizarMaster(7, dto);

        assertThat(m.getNombreBase()).isEqualTo("Samsung A16 Azul");
    }
}
