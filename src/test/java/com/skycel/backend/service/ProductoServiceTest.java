package com.skycel.backend.service;

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

    private StockAjusteDTO dto(String tipo, BigDecimal cantidad, List<String> imeis, String comentario) {
        StockAjusteDTO d = new StockAjusteDTO();
        d.setTipo(tipo);
        d.setCantidad(cantidad);
        d.setImeis(imeis);
        d.setComentario(comentario);
        return d;
    }

    // ── Producto no encontrado ──────────────────────────────────────────────

    @Test
    @DisplayName("ajustarStock: producto inexistente lanza 404")
    void ajustarStock_productoNoEncontrado_lanza404() {
        when(productoRepository.findByCodpro("NO-EXISTE")).thenReturn(Optional.empty());

        StockAjusteDTO request = dto("ENTRADA", BigDecimal.ONE, List.of("123456789012345"), null);

        assertThatThrownBy(() -> productoService.ajustarStock("NO-EXISTE", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrado");

        verifyNoInteractions(productoImeiRepository);
    }

    // ── SERVICIO ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ajustarStock: los artículos de tipo SERVICIO no admiten ajuste de stock")
    void ajustarStock_servicio_lanzaBadRequest() {
        Producto p = servicio();
        when(productoRepository.findByCodpro(p.getCodpro())).thenReturn(Optional.of(p));

        StockAjusteDTO request = dto("ENTRADA", BigDecimal.ONE, null, null);

        assertThatThrownBy(() -> productoService.ajustarStock(p.getCodpro(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("SERVICIO");

        verify(productoRepository, never()).save(any());
    }

    // ── CELULAR — ENTRADA ────────────────────────────────────────────────────

    @Nested
    @DisplayName("ajustarStock — CELULAR / ENTRADA")
    class CelularEntrada {

        @Test
        @DisplayName("con IMEIs válidos y nuevos: suma stock y registra cada IMEI como DISPONIBLE")
        void entrada_imeisValidos_sumaStockYRegistraImeis() {
            Producto p = celular(BigDecimal.valueOf(2));
            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));
            when(productoImeiRepository.existsByImei(anyString())).thenReturn(false);

            List<String> imeis = List.of("123456789012345", "123456789012346");
            StockAjusteDTO request = dto("ENTRADA", BigDecimal.valueOf(2), imeis, null);

            productoService.ajustarStock(CODPRO, request);

            assertThat(p.getStock()).isEqualByComparingTo("4");
            verify(productoImeiRepository, times(2)).save(imeiCaptor.capture());
            assertThat(imeiCaptor.getAllValues())
                    .extracting(ProductoImei::getImei)
                    .containsExactlyInAnyOrderElementsOf(imeis);
            assertThat(imeiCaptor.getAllValues())
                    .allMatch(pi -> "DISPONIBLE".equals(pi.getEstado()));
        }

        @Test
        @DisplayName("si el número de IMEIs no coincide con la cantidad: 400")
        void entrada_cantidadImeisNoCoincide_lanzaBadRequest() {
            Producto p = celular(BigDecimal.ZERO);
            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));

            StockAjusteDTO request = dto("ENTRADA", BigDecimal.valueOf(2),
                    List.of("123456789012345"), null); // solo 1 IMEI, se piden 2

            assertThatThrownBy(() -> productoService.ajustarStock(CODPRO, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("exactamente 2 IMEIs");

            verifyNoInteractions(productoImeiRepository);
        }

        @ParameterizedTest(name = "IMEI/Serie inválido: \"{0}\"")
        @ValueSource(strings = {"abc", "1234", "123456789012345678901", "no-valido!"})
        @DisplayName("rechaza identificadores que no tengan entre 5 y 20 caracteres alfanuméricos")
        void entrada_imeiFormatoInvalido_lanzaBadRequest(String imeiInvalido) {
            Producto p = celular(BigDecimal.ZERO);
            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));

            StockAjusteDTO request = dto("ENTRADA", BigDecimal.ONE, List.of(imeiInvalido), null);

            assertThatThrownBy(() -> productoService.ajustarStock(CODPRO, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("5 y 20 caracteres");
        }

        @Test
        @DisplayName("si un IMEI ya existe en el sistema: 409 y no se guarda ninguno")
        void entrada_imeiDuplicado_lanzaConflict() {
            Producto p = celular(BigDecimal.ZERO);
            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));
            when(productoImeiRepository.existsByImei("123456789012345")).thenReturn(false);
            when(productoImeiRepository.existsByImei("123456789012346")).thenReturn(true);

            StockAjusteDTO request = dto("ENTRADA", BigDecimal.valueOf(2),
                    List.of("123456789012345", "123456789012346"), null);

            assertThatThrownBy(() -> productoService.ajustarStock(CODPRO, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("ya está registrado");

            // Regla actual del servicio: valida TODOS antes de guardar ninguno
            verify(productoImeiRepository, never()).save(any());
        }

        @Test
        @DisplayName("cantidad no entera (p.ej. 1.5) para un celular: 400")
        void entrada_cantidadNoEntera_lanzaBadRequest() {
            Producto p = celular(BigDecimal.ZERO);
            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));

            StockAjusteDTO request = dto("ENTRADA", new BigDecimal("1.5"), null, null);

            assertThatThrownBy(() -> productoService.ajustarStock(CODPRO, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("número entero");
        }
    }

    // ── CELULAR — SALIDA ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("ajustarStock — CELULAR / SALIDA")
    class CelularSalida {

        @Test
        @DisplayName("con IMEI disponible del mismo producto: resta stock y marca DEBAJA por defecto")
        void salida_imeiDisponible_marcaDebajaPorDefecto() {
            Producto p = celular(BigDecimal.valueOf(3));
            ProductoImei pi = ProductoImei.builder()
                    .id(1L).producto(p).imei("123456789012345").estado("DISPONIBLE").build();

            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));
            when(productoImeiRepository.findByImei("123456789012345")).thenReturn(Optional.of(pi));

            StockAjusteDTO request = dto("SALIDA", BigDecimal.ONE, List.of("123456789012345"), null);

            productoService.ajustarStock(CODPRO, request);

            assertThat(p.getStock()).isEqualByComparingTo("2");
            assertThat(pi.getEstado()).isEqualTo("DEBAJA");
        }

        @Test
        @DisplayName("si el comentario menciona VENTA: marca el IMEI como VENDIDO en vez de DEBAJA")
        void salida_comentarioVenta_marcaVendido() {
            Producto p = celular(BigDecimal.ONE);
            ProductoImei pi = ProductoImei.builder()
                    .id(1L).producto(p).imei("123456789012345").estado("DISPONIBLE").build();

            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));
            when(productoImeiRepository.findByImei("123456789012345")).thenReturn(Optional.of(pi));

            StockAjusteDTO request = dto("SALIDA", BigDecimal.ONE, List.of("123456789012345"), "Venta de contado");

            productoService.ajustarStock(CODPRO, request);

            assertThat(pi.getEstado()).isEqualTo("VENDIDO");
        }

        @Test
        @DisplayName("si el IMEI pertenece a otro producto: 400 y no se toca el stock")
        void salida_imeiDeOtroProducto_lanzaBadRequest() {
            Producto p = celular(BigDecimal.ONE);
            Producto otro = celular(BigDecimal.ZERO);
            otro.setIdproducto(999);
            ProductoImei pi = ProductoImei.builder()
                    .id(1L).producto(otro).imei("123456789012345").estado("DISPONIBLE").build();

            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));
            when(productoImeiRepository.findByImei("123456789012345")).thenReturn(Optional.of(pi));

            StockAjusteDTO request = dto("SALIDA", BigDecimal.ONE, List.of("123456789012345"), null);

            assertThatThrownBy(() -> productoService.ajustarStock(CODPRO, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no pertenece a este producto");

            assertThat(p.getStock()).isEqualByComparingTo("1");
        }

        @Test
        @DisplayName("si el IMEI ya no está DISPONIBLE (p.ej. ya vendido): 400")
        void salida_imeiNoDisponible_lanzaBadRequest() {
            Producto p = celular(BigDecimal.ONE);
            ProductoImei pi = ProductoImei.builder()
                    .id(1L).producto(p).imei("123456789012345").estado("VENDIDO").build();

            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));
            when(productoImeiRepository.findByImei("123456789012345")).thenReturn(Optional.of(pi));

            StockAjusteDTO request = dto("SALIDA", BigDecimal.ONE, List.of("123456789012345"), null);

            assertThatThrownBy(() -> productoService.ajustarStock(CODPRO, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no está disponible");
        }

        @Test
        @DisplayName("IMEI inexistente en salida: 404")
        void salida_imeiInexistente_lanza404() {
            Producto p = celular(BigDecimal.ONE);
            when(productoRepository.findByCodpro(CODPRO)).thenReturn(Optional.of(p));
            when(productoImeiRepository.findByImei("999999999999999")).thenReturn(Optional.empty());

            StockAjusteDTO request = dto("SALIDA", BigDecimal.ONE, List.of("999999999999999"), null);

            assertThatThrownBy(() -> productoService.ajustarStock(CODPRO, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no existe");
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
            when(productoRepository.findByCodpro(p.getCodpro())).thenReturn(Optional.of(p));

            productoService.ajustarStock(p.getCodpro(), dto("ENTRADA", BigDecimal.TEN, null, null));

            assertThat(p.getStock()).isEqualByComparingTo("15");
            verifyNoInteractions(productoImeiRepository);
        }

        @Test
        @DisplayName("SALIDA con stock insuficiente: 400 y no queda en negativo")
        void salida_stockInsuficiente_lanzaBadRequest() {
            Producto p = accesorio(BigDecimal.valueOf(3));
            when(productoRepository.findByCodpro(p.getCodpro())).thenReturn(Optional.of(p));

            assertThatThrownBy(() ->
                    productoService.ajustarStock(p.getCodpro(), dto("SALIDA", BigDecimal.TEN, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Stock insuficiente");

            assertThat(p.getStock()).isEqualByComparingTo("3");
        }

        @Test
        @DisplayName("AJUSTE fija el stock al valor exacto enviado")
        void ajuste_fijaValorExacto() {
            Producto p = accesorio(BigDecimal.valueOf(3));
            when(productoRepository.findByCodpro(p.getCodpro())).thenReturn(Optional.of(p));

            productoService.ajustarStock(p.getCodpro(), dto("AJUSTE", BigDecimal.valueOf(100), null, null));

            assertThat(p.getStock()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("tipo de ajuste desconocido: 400")
        void tipoDesconocido_lanzaBadRequest() {
            Producto p = accesorio(BigDecimal.ZERO);
            when(productoRepository.findByCodpro(p.getCodpro())).thenReturn(Optional.of(p));

            assertThatThrownBy(() ->
                    productoService.ajustarStock(p.getCodpro(), dto("TRASPASO", BigDecimal.ONE, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("inválido");
        }
    }
}
