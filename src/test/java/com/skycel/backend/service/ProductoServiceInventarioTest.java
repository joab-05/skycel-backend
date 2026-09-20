package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ImeiUpdateDTO;
import com.skycel.backend.domain.dto.request.ProductoMasterUpdateDTO;
import com.skycel.backend.domain.dto.request.ProductoRequestDTO;
import com.skycel.backend.domain.dto.request.ProductoUpdateDTO;
import com.skycel.backend.domain.dto.request.UnidadEquipoDTO;
import com.skycel.backend.domain.dto.response.ImeiInfoDTO;
import com.skycel.backend.domain.dto.response.ProductoMasterResponseDTO;
import com.skycel.backend.domain.dto.response.ProductoResponseDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.domain.mapper.ProductoMapper;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas de las reglas de inventario: códigos consecutivos de equipos, condición y costo por unidad,
 * compatibilidad, tiempo estimado, garantía y stock mínimo. Sin Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class ProductoServiceInventarioTest {

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

    @InjectMocks
    private ProductoService service;

    private Tienda tienda;
    private Magnitud unidad;

    @BeforeEach
    void setUp() {
        tienda = Tienda.builder().codti(2).nombre("Zocalo").build();
        unidad = Magnitud.builder().idmagnitud((short) 1).nombre("UNIDAD").build();
        lenient().when(tiendaRepository.findById(2)).thenReturn(Optional.of(tienda));
        lenient().when(magnitudRepository.findById((short) 1)).thenReturn(Optional.of(unidad));
        lenient().when(productoMapper.toProductoEntity(any(ProductoRequestDTO.class))).thenAnswer(inv -> {
            ProductoRequestDTO d = inv.getArgument(0);
            return Producto.builder().stock(d.getStock()).preciopro(d.getPrecioCompra()).preciopub(d.getPrecioVenta())
                    .stockMinimo(d.getStockMinimo()).build();
        });
        lenient().when(productoMapper.toProductoResponse(any(Producto.class))).thenAnswer(inv -> new ProductoResponseDTO());
        lenient().when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productoImeiRepository.save(any(ProductoImei.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProductoMaster master(TipoProducto tipo) {
        Categoria cat = Categoria.builder().idcat((short) 2).nombre("Cargadores").codigo("CAR").build();
        return ProductoMaster.builder().idprodmaster(5).tipo(tipo).nombreBase("Artículo").categoria(cat).build();
    }

    private ProductoRequestDTO requestSobreMaster(ProductoMaster master, String stock) {
        when(productoMasterRepository.findById(master.getIdprodmaster())).thenReturn(Optional.of(master));
        ProductoRequestDTO dto = new ProductoRequestDTO();
        dto.setCodti(2);
        dto.setIdProductoMaster(master.getIdprodmaster().longValue());
        dto.setStock(new BigDecimal(stock));
        dto.setPrecioCompra(new BigDecimal("100"));
        dto.setPrecioVenta(new BigDecimal("150"));
        return dto;
    }

    private UnidadEquipoDTO unidad(String imei, String condicion, String costo, String precio) {
        UnidadEquipoDTO u = new UnidadEquipoDTO();
        u.setImei(imei);
        u.setCondicion(condicion);
        u.setCostoUnitario(costo == null ? null : new BigDecimal(costo));
        u.setPrecioVenta(precio == null ? null : new BigDecimal(precio));
        return u;
    }

    // ── Códigos consecutivos ─────────────────────────────────────────────────

    @Nested
    @DisplayName("código de un equipo")
    class CodigoEquipo {

        @Test
        @DisplayName("es consecutivo (CEL-000007) con el contador propio de equipos, no aleatorio")
        void codigoConsecutivo() {
            when(categoriaFolioRepository.obtenerUltimoFolioGenerado((short) 0)).thenReturn(7L);
            ProductoRequestDTO dto = requestSobreMaster(master(TipoProducto.CELULAR), "1");
            dto.setImeis(List.of("350000000000011"));

            service.crearProducto(dto);

            verify(categoriaFolioRepository).incrementar((short) 0);
            ArgumentCaptor<Producto> cap = ArgumentCaptor.forClass(Producto.class);
            verify(productoRepository).save(cap.capture());
            assertThat(cap.getValue().getCodpro()).isEqualTo("CEL-000007");
        }

        @Test
        @DisplayName("un accesorio sigue usando el código de su categoría (CAR-000003)")
        void accesorioUsaLaCategoria() {
            when(categoriaFolioRepository.obtenerUltimoFolioGenerado((short) 2)).thenReturn(3L);

            service.crearProducto(requestSobreMaster(master(TipoProducto.ACCESORIO), "10"));

            ArgumentCaptor<Producto> cap = ArgumentCaptor.forClass(Producto.class);
            verify(productoRepository).save(cap.capture());
            assertThat(cap.getValue().getCodpro()).isEqualTo("CAR-000003");
        }
    }

    // ── Condición y costo por unidad ─────────────────────────────────────────

    @Nested
    @DisplayName("unidades de equipo: condición, costo y precio propios")
    class Unidades {

        private ProductoRequestDTO celular(String stock) {
            when(categoriaFolioRepository.obtenerUltimoFolioGenerado((short) 0)).thenReturn(1L);
            return requestSobreMaster(master(TipoProducto.CELULAR), stock);
        }

        @Test
        @DisplayName("lista simple de IMEIs: todas las unidades quedan NUEVO, sin costo ni precio propios")
        void imeisSimples_sonNuevos() {
            ProductoRequestDTO dto = celular("2");
            dto.setImeis(List.of("350000000000011", "350000000000029"));

            service.crearProducto(dto);

            ArgumentCaptor<ProductoImei> cap = ArgumentCaptor.forClass(ProductoImei.class);
            verify(productoImeiRepository, times(2)).save(cap.capture());
            assertThat(cap.getAllValues()).allSatisfy(pi -> {
                assertThat(pi.getCondicion()).isEqualTo("NUEVO");
                assertThat(pi.getCostoUnitario()).isNull();
                assertThat(pi.getPrecioVentaOverride()).isNull();
                assertThat(pi.getEstado()).isEqualTo("DISPONIBLE");
            });
        }

        @Test
        @DisplayName("unidades con datos propios: un usado con su costo de compra y su precio")
        void unidades_usadoConCostoYPrecio() {
            ProductoRequestDTO dto = celular("2");
            dto.setUnidades(List.of(
                    unidad("350000000000011", "nuevo", null, null),
                    unidad("350000000000029", "usado", "8000", "9500")));

            service.crearProducto(dto);

            ArgumentCaptor<ProductoImei> cap = ArgumentCaptor.forClass(ProductoImei.class);
            verify(productoImeiRepository, times(2)).save(cap.capture());
            ProductoImei nuevo = cap.getAllValues().get(0);
            ProductoImei usado = cap.getAllValues().get(1);
            assertThat(nuevo.getCondicion()).isEqualTo("NUEVO");
            assertThat(usado.getCondicion()).isEqualTo("USADO");
            assertThat(usado.getCostoUnitario()).isEqualByComparingTo("8000");
            assertThat(usado.getPrecioVentaOverride()).isEqualByComparingTo("9500");
        }

        @Test
        @DisplayName("enviar 'imeis' y 'unidades' a la vez: 400")
        void ambasListas() {
            ProductoRequestDTO dto = celular("1");
            dto.setImeis(List.of("350000000000011"));
            dto.setUnidades(List.of(unidad("350000000000029", null, null, null)));

            assertThatThrownBy(() -> service.crearProducto(dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no ambas");
            verify(productoImeiRepository, never()).save(any());
        }

        @Test
        @DisplayName("condición inválida: 400")
        void condicionInvalida() {
            ProductoRequestDTO dto = celular("1");
            dto.setUnidades(List.of(unidad("350000000000011", "roto", null, null)));

            assertThatThrownBy(() -> service.crearProducto(dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Condición inválida");
        }

        @Test
        @DisplayName("costo negativo o precio en 0: 400")
        void costoOPrecioInvalidos() {
            ProductoRequestDTO conCosto = celular("1");
            conCosto.setUnidades(List.of(unidad("350000000000011", "usado", "-1", null)));
            assertThatThrownBy(() -> service.crearProducto(conCosto))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("costo");

            ProductoRequestDTO conPrecio = celular("1");
            conPrecio.setUnidades(List.of(unidad("350000000000011", "usado", "100", "0")));
            assertThatThrownBy(() -> service.crearProducto(conPrecio))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("precio");
        }

        @Test
        @DisplayName("la cantidad de unidades debe coincidir con el stock")
        void cantidadNoCoincide() {
            ProductoRequestDTO dto = celular("3");
            dto.setUnidades(List.of(unidad("350000000000011", null, null, null)));

            assertThatThrownBy(() -> service.crearProducto(dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("exactamente 3");
        }

        @Test
        @DisplayName("PATCH de una unidad disponible: cambia condición y costo, y la respuesta muestra el costo propio")
        void actualizarImei() {
            Producto p = Producto.builder().idproducto(1).preciopro(new BigDecimal("14000")).preciopub(new BigDecimal("16999")).build();
            ProductoImei pi = ProductoImei.builder().id(1L).producto(p).imei("350000000000011").estado("DISPONIBLE").build();
            when(productoImeiRepository.findByImei("350000000000011")).thenReturn(Optional.of(pi));

            ImeiUpdateDTO dto = new ImeiUpdateDTO();
            dto.setCondicion("reacondicionado");
            dto.setCostoUnitario(new BigDecimal("9000"));
            ImeiInfoDTO r = service.actualizarImei("350000000000011", dto);

            assertThat(pi.getCondicion()).isEqualTo("REACONDICIONADO");
            assertThat(r.getCondicion()).isEqualTo("REACONDICIONADO");
            assertThat(r.getCostoUnitario()).isEqualByComparingTo("9000");
            assertThat(r.getTieneCostoPropio()).isTrue();
            assertThat(r.getPrecioVenta()).isEqualByComparingTo("16999"); // sin precio propio: el del modelo
        }

        @Test
        @DisplayName("sin costo propio, la unidad muestra el costo del modelo")
        void costoDelModeloPorDefecto() {
            Producto p = Producto.builder().idproducto(1).preciopro(new BigDecimal("14000")).preciopub(new BigDecimal("16999")).build();
            ProductoImei pi = ProductoImei.builder().id(1L).producto(p).imei("350000000000011").estado("DISPONIBLE").build();
            when(productoImeiRepository.findByImei("350000000000011")).thenReturn(Optional.of(pi));

            ImeiInfoDTO r = service.actualizarImei("350000000000011", new ImeiUpdateDTO());

            assertThat(r.getCostoUnitario()).isEqualByComparingTo("14000");
            assertThat(r.getTieneCostoPropio()).isFalse();
            assertThat(r.getCondicion()).isEqualTo("NUEVO");
        }

        @Test
        @DisplayName("una unidad vendida no se puede modificar: 400")
        void unidadVendida() {
            ProductoImei pi = ProductoImei.builder().id(1L).producto(new Producto()).imei("350000000000011").estado("VENDIDO").build();
            when(productoImeiRepository.findByImei("350000000000011")).thenReturn(Optional.of(pi));

            assertThatThrownBy(() -> service.actualizarImei("350000000000011", new ImeiUpdateDTO()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("unidad disponible");
        }
    }

    // ── Stock mínimo ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stock mínimo")
    class StockMinimo {

        @Test
        @DisplayName("se guarda al crear el producto")
        void seGuardaAlCrear() {
            when(categoriaFolioRepository.obtenerUltimoFolioGenerado((short) 2)).thenReturn(1L);
            ProductoRequestDTO dto = requestSobreMaster(master(TipoProducto.ACCESORIO), "10");
            dto.setStockMinimo(new BigDecimal("5"));

            service.crearProducto(dto);

            ArgumentCaptor<Producto> cap = ArgumentCaptor.forClass(Producto.class);
            verify(productoRepository).save(cap.capture());
            assertThat(cap.getValue().getStockMinimo()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("sin indicarlo queda en 0 (sin alerta)")
        void pordefectoCero() {
            when(categoriaFolioRepository.obtenerUltimoFolioGenerado((short) 2)).thenReturn(1L);

            service.crearProducto(requestSobreMaster(master(TipoProducto.ACCESORIO), "10"));

            ArgumentCaptor<Producto> cap = ArgumentCaptor.forClass(Producto.class);
            verify(productoRepository).save(cap.capture());
            assertThat(cap.getValue().getStockMinimo()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("negativo: 400; en un servicio con valor mayor a 0: 400")
        void invalidos() {
            ProductoRequestDTO negativo = requestSobreMaster(master(TipoProducto.ACCESORIO), "10");
            negativo.setStockMinimo(new BigDecimal("-1"));
            assertThatThrownBy(() -> service.crearProducto(negativo))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("negativo");

            ProductoMaster srv = master(TipoProducto.SERVICIO);
            srv.setIdprodmaster(6);
            ProductoRequestDTO servicio = requestSobreMaster(srv, "0");
            servicio.setStockMinimo(new BigDecimal("2"));
            assertThatThrownBy(() -> service.crearProducto(servicio))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no aplica a servicios");
        }

        @Test
        @DisplayName("actualizar el umbral de un producto existente")
        void actualizarUmbral() {
            Producto p = Producto.builder().idproducto(3).codpro("ACC-000001").tienda(tienda)
                    .productoMaster(master(TipoProducto.ACCESORIO)).stock(new BigDecimal("4")).build();
            when(productoRepository.findByCodpro("ACC-000001")).thenReturn(Optional.of(p));
            ProductoUpdateDTO dto = new ProductoUpdateDTO();
            dto.setStockMinimo(new BigDecimal("6"));

            ProductoResponseDTO r = service.actualizar("ACC-000001", dto);

            assertThat(p.getStockMinimo()).isEqualByComparingTo("6");
            assertThat(r.getBajoStock()).isTrue();   // 4 <= 6
        }

        @Test
        @DisplayName("bajoStock: verdadero cuando stock <= mínimo; falso sin umbral, con stock arriba del umbral o en servicios")
        void bajoStock() {
            ProductoMaster acc = master(TipoProducto.ACCESORIO);
            Producto justo   = Producto.builder().idproducto(1).tienda(tienda).productoMaster(acc)
                    .stock(new BigDecimal("5")).stockMinimo(new BigDecimal("5")).build();
            Producto arriba  = Producto.builder().idproducto(2).tienda(tienda).productoMaster(acc)
                    .stock(new BigDecimal("6")).stockMinimo(new BigDecimal("5")).build();
            Producto sinUmbral = Producto.builder().idproducto(3).tienda(tienda).productoMaster(acc)
                    .stock(new BigDecimal("0")).build();
            Producto servicio = Producto.builder().idproducto(4).tienda(tienda).productoMaster(master(TipoProducto.SERVICIO))
                    .stock(BigDecimal.ZERO).stockMinimo(new BigDecimal("3")).build();
            when(productoRepository.findBajoStockByTienda(2)).thenReturn(List.of(justo, arriba, sinUmbral, servicio));

            List<ProductoResponseDTO> r = service.obtenerBajoStock(2);

            assertThat(r).extracting(ProductoResponseDTO::getBajoStock).containsExactly(true, false, false, false);
            assertThat(r.get(2).getStockMinimo()).isEqualByComparingTo("0");
        }
    }

    // ── Compatibilidad, tiempo estimado y garantía ───────────────────────────

    @Nested
    @DisplayName("atributos del maestro: compatibilidad, tiempo estimado y garantía")
    class AtributosMaster {

        @Test
        @DisplayName("PATCH del maestro de un servicio: cambia los tres atributos")
        void servicio() {
            ProductoMaster m = master(TipoProducto.SERVICIO);
            when(productoMasterRepository.findById(5)).thenReturn(Optional.of(m));
            when(productoMasterRepository.save(any(ProductoMaster.class))).thenAnswer(i -> i.getArgument(0));
            when(productoMapper.toResponse(any(ProductoMaster.class))).thenReturn(new ProductoMasterResponseDTO());
            ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
            dto.setCompatibilidad("  iPhone 13  ");
            dto.setTiempoEstimadoMin(60);
            dto.setDiasGarantia(30);

            service.actualizarMaster(5, dto);

            assertThat(m.getCompatibilidad()).isEqualTo("iPhone 13");
            assertThat(m.getTiempoEstimadoMin()).isEqualTo(60);
            assertThat(m.getDiasGarantia()).isEqualTo(30);
        }

        @Test
        @DisplayName("el tiempo estimado no aplica a un accesorio: 400")
        void tiempoEnAccesorio() {
            when(productoMasterRepository.findById(5)).thenReturn(Optional.of(master(TipoProducto.ACCESORIO)));
            ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
            dto.setTiempoEstimadoMin(30);

            assertThatThrownBy(() -> service.actualizarMaster(5, dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("solo aplica a servicios");
        }

        @Test
        @DisplayName("maestro inexistente: 404; garantía negativa: 400")
        void errores() {
            when(productoMasterRepository.findById(99)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.actualizarMaster(99, new ProductoMasterUpdateDTO()))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

            when(productoMasterRepository.findById(5)).thenReturn(Optional.of(master(TipoProducto.ACCESORIO)));
            ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
            dto.setDiasGarantia(-1);
            assertThatThrownBy(() -> service.actualizarMaster(5, dto))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("garantía");
        }

        @Test
        @DisplayName("accesorios compatibles: exige el modelo; con modelo consulta la tienda")
        void compatibles() {
            assertThatThrownBy(() -> service.obtenerAccesoriosCompatibles(2, "  "))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("modelo");

            Producto p = Producto.builder().idproducto(1).tienda(tienda).productoMaster(master(TipoProducto.ACCESORIO))
                    .stock(BigDecimal.TEN).build();
            when(productoRepository.findAccesoriosCompatibles(2, "iPhone 13")).thenReturn(List.of(p));

            assertThat(service.obtenerAccesoriosCompatibles(2, " iPhone 13 ")).hasSize(1);
        }
    }
}
