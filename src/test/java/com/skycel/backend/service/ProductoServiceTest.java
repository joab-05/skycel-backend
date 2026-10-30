package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ProductoMasterUpdateDTO;
import com.skycel.backend.domain.dto.request.ProductoUpdateDTO;
import com.skycel.backend.domain.dto.request.StockAjusteDTO;
import com.skycel.backend.domain.dto.response.ProductoResponseDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.domain.mapper.ProductoMapper;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de ProductoService, con foco en ajustarStock():
 * es la operación con más reglas de negocio (IMEI, tipos de producto,
 * validación de stock) y hoy no tenía ninguna cobertura.
 *
 * No se levanta contexto de Spring ni base de datos: todos los repos
 * y el mapper están mockeados con Mockito.
 */
@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock private ProductoMasterRepository productoMasterRepository;
    @Mock private ProductoRepository       productoRepository;
    @Mock private CategoriaRepository      categoriaRepository;
    @Mock private TiendaRepository         tiendaRepository;
    @Mock private ColorRepository          colorRepository;
    @Mock private MagnitudRepository       magnitudRepository;
    @Mock private ProveedorRepository      proveedorRepository;
    @Mock private SeccionRepository        seccionRepository;
    @Mock private ProductoImeiRepository   productoImeiRepository;
    @Mock private ProductoMapper           productoMapper;
    @Mock private MovimientoInventarioService movimientoInventarioService;
    @Mock private DescuentoService         descuentoService;

    @InjectMocks
    private ProductoService productoService;

    @Captor private ArgumentCaptor<ProductoImei> imeiCaptor;
    @Captor private ArgumentCaptor<Producto> productoCaptor;

    private static final String CODPRO = "CEL-ABC12345";

    @BeforeEach
    void setUp() {
        // El mapper es generado (MapStruct); en pruebas unitarias solo nos
        // interesa que no reviente con NPE — devolvemos un DTO "vacío" que
        // el propio servicio va rellenando (ver ProductoService#toDto).
        lenient().when(productoMapper.toProductoResponse(any(Producto.class)))
                .thenAnswer(inv -> new ProductoResponseDTO());
        lenient().when(productoRepository.save(any(Producto.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Producto celular(BigDecimal stockInicial) {
        ProductoMaster master = ProductoMaster.builder()
                .idprodmaster(1)
                .tipo(TipoProducto.CELULAR)
                .nombreBase("iPhone 13")
                .build();
        return Producto.builder()
                .idproducto(10)
                .codpro(CODPRO)
                .productoMaster(master)
                .stock(stockInicial)
                .build();
    }

    private Producto accesorio(BigDecimal stockInicial) {
        ProductoMaster master = ProductoMaster.builder()
                .idprodmaster(2)
                .tipo(TipoProducto.ACCESORIO)
                .nombreBase("Cable USB-C")
                .build();
        return Producto.builder()
                .idproducto(20)
                .codpro("ACC-XYZ99999")
                .productoMaster(master)
                .stock(stockInicial)
                .build();
    }

    private Producto servicio() {
        ProductoMaster master = ProductoMaster.builder()
                .idprodmaster(3)
                .tipo(TipoProducto.SERVICIO)
                .nombreBase("Cambio de pantalla")
                .build();
        return Producto.builder()
                .idproducto(30)
                .codpro("SRV-AAA11111")
                .productoMaster(master)
                .stock(BigDecimal.ZERO)
                .build();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("un ajuste de existencias sin motivo: 400 (todo cambio manual lleva su motivo)")
    void ajusteSinMotivo_lanzaBadRequest() {
        assertThatThrownBy(() -> productoService.ajustarStock("ACC-0001", dto("SALIDA", BigDecimal.ONE, null, "  ")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("motivo");
        assertThatThrownBy(() -> productoService.ajustarStock("ACC-0001", dto("AJUSTE", BigDecimal.ONE, null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("motivo");
    }

    private StockAjusteDTO dto(String tipo, BigDecimal cantidad, List<String> imeis, String comentario) {
        StockAjusteDTO d = new StockAjusteDTO();
        d.setTipo(tipo);
        d.setCantidad(cantidad);
        d.setImeis(imeis);
        d.setComentario(comentario);
        return d;
    }

    // ── Mismo código en varias sucursales ───────────────────────────────────

    private Producto enSucursal(Producto base, int codti, int idproducto) {
        return Producto.builder()
                .idproducto(idproducto).codpro(base.getCodpro()).productoMaster(base.getProductoMaster())
                .tienda(Tienda.builder().codti(codti).build())
                .stock(BigDecimal.TEN).build();
    }

    @Test
    @DisplayName("ajustarStock: un código que existe en varias sucursales exige codti (400)")
    void ajustarStock_codigoEnVariasSucursales_sinCodti_lanza400() {
        Producto base = accesorio(BigDecimal.TEN);
        when(productoRepository.findAllByCodpro(base.getCodpro()))
                .thenReturn(List.of(enSucursal(base, 1, 20), enSucursal(base, 2, 21)));

        assertThatThrownBy(() -> productoService.ajustarStock(base.getCodpro(), dto("ENTRADA", BigDecimal.ONE, null, "motivo de prueba")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("ajustarStock: con codti mueve el stock de la sucursal indicada y no toca la otra")
    void ajustarStock_codigoEnVariasSucursales_conCodti_usaEsaSucursal() {
        Producto base = accesorio(BigDecimal.TEN);
        Producto zocalo = enSucursal(base, 2, 21);
        Producto bodega = enSucursal(base, 1, 20);
        when(productoRepository.findByCodproAndTienda_Codti(base.getCodpro(), 2)).thenReturn(Optional.of(zocalo));

        productoService.ajustarStock(base.getCodpro(), 2, dto("ENTRADA", new BigDecimal("5"), null, "reposición"));

        assertThat(zocalo.getStock()).isEqualByComparingTo("15");
        assertThat(bodega.getStock()).isEqualByComparingTo("10");
    }

    // ── Editar precios y datos del artículo ─────────────────────────────────

    @Test
    @DisplayName("actualizar: un precio de venta de $0 o negativo se rechaza (400)")
    void actualizar_precioVentaNoPositivo_lanza400() {
        ProductoUpdateDTO dto = new ProductoUpdateDTO();
        dto.setPrecioVenta(BigDecimal.ZERO);

        assertThatThrownBy(() -> productoService.actualizar("X", 1, dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("actualizar con aTodasLasSucursales: el precio se aplica también a las demás sucursales con ese código")
    void actualizar_aTodasLasSucursales_aplicaElPrecioEnTodas() {
        Producto base = accesorio(BigDecimal.TEN);
        Producto zocalo = enSucursal(base, 2, 21);
        Producto bodega = enSucursal(base, 1, 20);
        zocalo.setPreciopub(new BigDecimal("15")); bodega.setPreciopub(new BigDecimal("15"));
        when(productoRepository.findByCodproAndTienda_Codti(base.getCodpro(), 2)).thenReturn(Optional.of(zocalo));
        when(productoRepository.findAllByCodpro(base.getCodpro())).thenReturn(List.of(bodega, zocalo));
        ProductoUpdateDTO dto = new ProductoUpdateDTO();
        dto.setPrecioVenta(new BigDecimal("25"));
        dto.setStockMinimo(new BigDecimal("3"));

        productoService.actualizar(base.getCodpro(), 2, true, dto);

        assertThat(zocalo.getPreciopub()).isEqualByComparingTo("25");
        assertThat(bodega.getPreciopub()).isEqualByComparingTo("25");
        assertThat(zocalo.getStockMinimo()).isEqualByComparingTo("3");   // lo demás solo cambia en la sucursal indicada
        assertThat(bodega.getStockMinimo()).isNull();
    }

    @Test
    @DisplayName("actualizar sin aTodasLasSucursales: solo cambia la sucursal indicada")
    void actualizar_soloUnaSucursal() {
        Producto base = accesorio(BigDecimal.TEN);
        Producto zocalo = enSucursal(base, 2, 21);
        Producto bodega = enSucursal(base, 1, 20);
        zocalo.setPreciopub(new BigDecimal("15")); bodega.setPreciopub(new BigDecimal("15"));
        when(productoRepository.findByCodproAndTienda_Codti(base.getCodpro(), 2)).thenReturn(Optional.of(zocalo));
        ProductoUpdateDTO dto = new ProductoUpdateDTO();
        dto.setPrecioVenta(new BigDecimal("25"));

        productoService.actualizar(base.getCodpro(), 2, false, dto);

        assertThat(zocalo.getPreciopub()).isEqualByComparingTo("25");
        assertThat(bodega.getPreciopub()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("actualizarMaster: renombrar a un nombre que ya usa otro artículo se rechaza (409)")
    void actualizarMaster_nombreRepetido_lanza409() {
        ProductoMaster mio = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.ACCESORIO).nombreBase("Cable USB-C").build();
        ProductoMaster otro = ProductoMaster.builder().idprodmaster(9).tipo(TipoProducto.ACCESORIO).nombreBase("Cable Tipo C").build();
        when(productoMasterRepository.findById(2)).thenReturn(Optional.of(mio));
        when(productoMasterRepository.findByNombreBaseIgnoreCaseAndActivoTrue("Cable Tipo C")).thenReturn(Optional.of(otro));
        ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
        dto.setNombreBase("  Cable   Tipo C ");

        assertThatThrownBy(() -> productoService.actualizarMaster(2, dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("actualizarMaster: se puede renombrar con un nombre libre")
    void actualizarMaster_renombrar() {
        ProductoMaster mio = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.ACCESORIO).nombreBase("Cable USB-C").build();
        when(productoMasterRepository.findById(2)).thenReturn(Optional.of(mio));
        when(productoMasterRepository.findByNombreBaseIgnoreCaseAndActivoTrue("Cable USB-C 60W")).thenReturn(Optional.empty());
        when(productoMasterRepository.save(any(ProductoMaster.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
        dto.setNombreBase("Cable USB-C 60W");

        productoService.actualizarMaster(2, dto);

        assertThat(mio.getNombreBase()).isEqualTo("Cable USB-C 60W");
    }

    @Test
    @DisplayName("actualizarMaster: una categoría de otro tipo de producto se rechaza (400)")
    void actualizarMaster_categoriaDeOtroTipo_lanza400() {
        ProductoMaster mio = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.ACCESORIO).nombreBase("Cable USB-C").build();
        Categoria deEquipos = Categoria.builder().idcat((short) 7).nombre("Celular").tipo(TipoProducto.CELULAR).build();
        when(productoMasterRepository.findById(2)).thenReturn(Optional.of(mio));
        when(categoriaRepository.findById((short) 7)).thenReturn(Optional.of(deEquipos));
        ProductoMasterUpdateDTO dto = new ProductoMasterUpdateDTO();
        dto.setIdCategoria((short) 7);

        assertThatThrownBy(() -> productoService.actualizarMaster(2, dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── Producto no encontrado ──────────────────────────────────────────────

    @Test
    @DisplayName("ajustarStock: producto inexistente lanza 404")
    void ajustarStock_productoNoEncontrado_lanza404() {
        when(productoRepository.findAllByCodpro("NO-EXISTE")).thenReturn(List.of());

        StockAjusteDTO request = dto("ENTRADA", BigDecimal.ONE, List.of("123456789012345"), "motivo de prueba");

        assertThatThrownBy(() -> productoService.ajustarStock("NO-EXISTE", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");

        verifyNoInteractions(productoImeiRepository);
    }

    // ── Marcado manual de rezagado ──────────────────────────────────────────

    @Test
    @DisplayName("marcarRezagado: pone la bandera y guarda el producto")
    void marcarRezagado_ponBandera() {
        Producto p = celular(BigDecimal.ONE);
        when(productoRepository.findAllByCodpro(CODPRO)).thenReturn(List.of(p));

        productoService.marcarRezagado(CODPRO, null, true);

        verify(productoRepository).save(productoCaptor.capture());
        assertThat(productoCaptor.getValue().getRezagado()).isTrue();
    }

    @Test
    @DisplayName("marcarRezagado: false la quita")
    void marcarRezagado_quitaBandera() {
        Producto p = celular(BigDecimal.ONE);
        p.setRezagado(true);
        when(productoRepository.findAllByCodpro(CODPRO)).thenReturn(List.of(p));

        productoService.marcarRezagado(CODPRO, null, false);

        verify(productoRepository).save(productoCaptor.capture());
        assertThat(productoCaptor.getValue().getRezagado()).isFalse();
    }

    // ── SERVICIO ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ajustarStock: los artículos de tipo SERVICIO no admiten ajuste de stock")
    void ajustarStock_servicio_lanzaBadRequest() {
        Producto p = servicio();
        when(productoRepository.findAllByCodpro(p.getCodpro())).thenReturn(List.of(p));

        StockAjusteDTO request = dto("ENTRADA", BigDecimal.ONE, null, "motivo de prueba");

        assertThatThrownBy(() -> productoService.ajustarStock(p.getCodpro(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("SERVICIO");

        verify(productoRepository, never()).save(any());
    }

    // ── CELULAR: ya no se ajusta por cantidad ────────────────────────────────

    @Nested
    @DisplayName("ajustarStock — un equipo se maneja por IMEI, no por cantidad")
    class Equipo {

        @Test
        @DisplayName("ENTRADA o SALIDA de un celular: 400, se hace desde Equipos (por IMEI o serie)")
        void ajusteDeUnEquipo_rechazado() {
            Producto p = celular(BigDecimal.valueOf(2));
            when(productoRepository.findAllByCodpro(CODPRO)).thenReturn(List.of(p));

            for (String tipo : List.of("ENTRADA", "SALIDA")) {
                assertThatThrownBy(() -> productoService.ajustarStock(CODPRO, dto(tipo, BigDecimal.ONE, null, "motivo de prueba")))
                        .isInstanceOf(ResponseStatusException.class)
                        .hasMessageContaining("IMEI");
            }
            assertThat(p.getStock()).isEqualByComparingTo("2");
        }
    }

    // ── ACCESORIO (sin IMEI) ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ajustarStock — ACCESORIO (sin control de IMEI)")
    class Accesorio {

        @Test
        @DisplayName("ENTRADA suma directamente al stock")
        void entrada_sumaStock() {
            Producto p = accesorio(BigDecimal.valueOf(5));
            when(productoRepository.findAllByCodpro(p.getCodpro())).thenReturn(List.of(p));

            productoService.ajustarStock(p.getCodpro(), dto("ENTRADA", BigDecimal.TEN, null, "motivo de prueba"));

            assertThat(p.getStock()).isEqualByComparingTo("15");
            verifyNoInteractions(productoImeiRepository);
        }

        @Test
        @DisplayName("SALIDA con stock insuficiente: 400 y no queda en negativo")
        void salida_stockInsuficiente_lanzaBadRequest() {
            Producto p = accesorio(BigDecimal.valueOf(3));
            when(productoRepository.findAllByCodpro(p.getCodpro())).thenReturn(List.of(p));

            assertThatThrownBy(() ->
                    productoService.ajustarStock(p.getCodpro(), dto("SALIDA", BigDecimal.TEN, null, "motivo de prueba")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Stock insuficiente");

            assertThat(p.getStock()).isEqualByComparingTo("3");
        }

        @Test
        @DisplayName("AJUSTE fija el stock al valor exacto enviado")
        void ajuste_fijaValorExacto() {
            Producto p = accesorio(BigDecimal.valueOf(3));
            when(productoRepository.findAllByCodpro(p.getCodpro())).thenReturn(List.of(p));

            productoService.ajustarStock(p.getCodpro(), dto("AJUSTE", BigDecimal.valueOf(100), null, "motivo de prueba"));

            assertThat(p.getStock()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("tipo de ajuste desconocido: 400")
        void tipoDesconocido_lanzaBadRequest() {
            Producto p = accesorio(BigDecimal.ZERO);
            when(productoRepository.findAllByCodpro(p.getCodpro())).thenReturn(List.of(p));

            assertThatThrownBy(() ->
                    productoService.ajustarStock(p.getCodpro(), dto("TRASPASO", BigDecimal.ONE, null, "motivo de prueba")))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("inválido");
        }
    }
}
