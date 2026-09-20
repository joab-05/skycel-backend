package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.venta.VentaDetalleRequestDto;
import com.skycel.backend.dto.venta.VentaRequestDto;
import com.skycel.backend.dto.venta.VentaResponseDto;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de VentaService. Sin Spring ni base de datos: todos los
 * repositorios están mockeados. Cubre las reglas acordadas para esta primera
 * versión del módulo:
 *  - 1 IMEI por línea para CELULAR (cantidad = 1).
 *  - Venta a crédito (monto abonado < total) exige cliente.
 *  - Cancelar revierte stock e IMEIs.
 */
@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock private VentaRepository          ventaRepository;
    @Mock private VentaDetalleRepository   ventaDetalleRepository;
    @Mock private ProductoRepository       productoRepository;
    @Mock private ProductoMasterRepository productoMasterRepository;
    @Mock private ProductoImeiRepository   productoImeiRepository;
    @Mock private TiendaRepository         tiendaRepository;
    @Mock private CajaRepository           cajaRepository;
    @Mock private ClienteRepository        clienteRepository;
    @Mock private UsuarioRepository        usuarioRepository;
    @Mock private MovimientoCajaService    movimientoCajaService;
    @Mock private CuentaPorCobrarService   cuentaPorCobrarService;
    @Mock private VentaPagoDetalleRepository ventaPagoDetalleRepository;
    @Mock private OrdenServicioRepository  ordenServicioRepository;
    @Mock private MovimientoInventarioService movimientoInventarioService;
    @Mock private com.skycel.backend.repository.DevolucionRepository devolucionRepository;

    @InjectMocks
    private VentaService ventaService;

    @Captor private ArgumentCaptor<Venta> ventaCaptor;
    @Captor private ArgumentCaptor<Producto> productoCaptor;
    @Captor private ArgumentCaptor<ProductoImei> imeiCaptor;

    private static final Integer CODTI = 1;
    private static final Integer ID_CAJA = 10;
    private static final Integer ID_VENDEDOR = 100;

    private Tienda tienda;
    private Caja caja;
    private Usuario vendedor;

    @BeforeEach
    void setUp() {
        tienda = Tienda.builder().codti(CODTI).nombre("Sucursal Centro").build();
        caja = Caja.builder().idCaja(ID_CAJA).tienda(tienda).nombreCaja("Caja 1").build();
        vendedor = Usuario.builder().idusuario(ID_VENDEDOR).username("jdoe").nombreCompleto("Juan Pérez").build();

        lenient().when(tiendaRepository.findById(CODTI)).thenReturn(Optional.of(tienda));
        lenient().when(cajaRepository.findById(ID_CAJA)).thenReturn(Optional.of(caja));
        lenient().when(usuarioRepository.findById(ID_VENDEDOR)).thenReturn(Optional.of(vendedor));

        // save(...) devuelve la misma entidad recibida, como haría JPA con un save simple.
        lenient().when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            if (v.getIdventa() == null) v.setIdventa(500);
            return v;
        });
        lenient().when(ventaDetalleRepository.save(any(VentaDetalle.class))).thenAnswer(inv -> {
            VentaDetalle d = inv.getArgument(0);
            if (d.getIddetalleVenta() == null) d.setIddetalleVenta(900);
            return d;
        });
        lenient().when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productoImeiRepository.save(any(ProductoImei.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProductoMaster masterCelular() {
        return ProductoMaster.builder().idprodmaster(1).tipo(TipoProducto.CELULAR).nombreBase("iPhone 13").build();
    }

    private ProductoMaster masterAccesorio() {
        return ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.ACCESORIO).nombreBase("Cable USB-C").build();
    }

    private Producto productoCelular(ProductoMaster master, BigDecimal stock) {
        return Producto.builder()
                .idproducto(10).codpro("CEL-0001").tienda(tienda).productoMaster(master)
                .stock(stock).preciopro(new BigDecimal("100")).preciopub(new BigDecimal("200"))
                .build();
    }

    private Producto productoAccesorio(ProductoMaster master, BigDecimal stock) {
        return Producto.builder()
                .idproducto(20).codpro("ACC-0001").tienda(tienda).productoMaster(master)
                .stock(stock).preciopro(new BigDecimal("5")).preciopub(new BigDecimal("15"))
                .build();
    }

    private VentaDetalleRequestDto lineaCelular(String imei) {
        VentaDetalleRequestDto d = new VentaDetalleRequestDto();
        d.setIdprodmaster(1);
        d.setCantidad((short) 1);
        d.setPrecioUnitarioFinal(new BigDecimal("199"));
        d.setImei(imei);
        return d;
    }

    private VentaDetalleRequestDto lineaAccesorio(short cantidad) {
        VentaDetalleRequestDto d = new VentaDetalleRequestDto();
        d.setIdprodmaster(2);
        d.setCantidad(cantidad);
        d.setPrecioUnitarioFinal(new BigDecimal("15"));
        return d;
    }

    private VentaRequestDto ventaBase(List<VentaDetalleRequestDto> detalles) {
        VentaRequestDto dto = new VentaRequestDto();
        dto.setCodti(CODTI);
        dto.setIdCaja(ID_CAJA);
        dto.setTipoComprobante((byte) 1);
        dto.setMetodoPago((byte) 1);
        dto.setDetalles(detalles);
        return dto;
    }

    // ── Crear — CELULAR ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("crear — CELULAR")
    class CrearCelular {

        @Test
        @DisplayName("con IMEI disponible: descuenta 1 de stock, marca el IMEI como VENDIDO y calcula el total")
        void ventaExitosa_descuentaStockYMarcaImeiVendido() {
            ProductoMaster master = masterCelular();
            Producto producto = productoCelular(master, BigDecimal.ONE);
            ProductoImei pi = ProductoImei.builder().id(1L).producto(producto).imei("123456789012345").estado("DISPONIBLE").build();

            when(productoMasterRepository.findById(1)).thenReturn(Optional.of(master));
            when(productoImeiRepository.findByImei("123456789012345")).thenReturn(Optional.of(pi));

            VentaRequestDto request = ventaBase(List.of(lineaCelular("123456789012345")));

            VentaResponseDto response = ventaService.crear(request, ID_VENDEDOR);

            assertThat(response.getTotal()).isEqualByComparingTo("199");
            assertThat(producto.getStock()).isEqualByComparingTo("0");
            assertThat(pi.getEstado()).isEqualTo("VENDIDO");
            verify(ventaDetalleRepository).save(any(VentaDetalle.class));
        }

        @Test
        @DisplayName("si el IMEI tiene precio propio (override), ese es el precio de referencia — no el del modelo")
        void imeiConPrecioPropio_usaEsePrecioComoBase() {
            ProductoMaster master = masterCelular();
            Producto producto = productoCelular(master, BigDecimal.ONE); // preciopub = 200
            ProductoImei pi = ProductoImei.builder().id(1L).producto(producto).imei("123456789012345")
                    .estado("DISPONIBLE").precioVentaOverride(new BigDecimal("150")).build(); // liquidación

            when(productoMasterRepository.findById(1)).thenReturn(Optional.of(master));
            when(productoImeiRepository.findByImei("123456789012345")).thenReturn(Optional.of(pi));

            VentaDetalleRequestDto linea = lineaCelular("123456789012345");
            linea.setPrecioUnitarioFinal(new BigDecimal("150")); // el cajero cobra el precio de liquidación
            VentaRequestDto request = ventaBase(List.of(linea));

            VentaResponseDto response = ventaService.crear(request, ID_VENDEDOR);

            assertThat(response.getDetalles().get(0).getPrecioUnitarioBase()).isEqualByComparingTo("150");
            assertThat(response.getTotal()).isEqualByComparingTo("150");
        }

        @Test
        @DisplayName("cantidad distinta de 1 en una línea de CELULAR: 400")
        void cantidadDistintaDeUno_lanzaBadRequest() {
            ProductoMaster master = masterCelular();
            when(productoMasterRepository.findById(1)).thenReturn(Optional.of(master));

            VentaDetalleRequestDto linea = lineaCelular("123456789012345");
            linea.setCantidad((short) 2);
            VentaRequestDto request = ventaBase(List.of(linea));

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("cantidad = 1");

            verify(ventaRepository, never()).save(any());
        }

        @Test
        @DisplayName("IMEI que ya no está DISPONIBLE: 400 y no se guarda la venta")
        void imeiNoDisponible_lanzaBadRequest() {
            ProductoMaster master = masterCelular();
            Producto producto = productoCelular(master, BigDecimal.ONE);
            ProductoImei pi = ProductoImei.builder().id(1L).producto(producto).imei("123456789012345").estado("VENDIDO").build();

            when(productoMasterRepository.findById(1)).thenReturn(Optional.of(master));
            when(productoImeiRepository.findByImei("123456789012345")).thenReturn(Optional.of(pi));

            VentaRequestDto request = ventaBase(List.of(lineaCelular("123456789012345")));

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no está disponible");

            verify(ventaRepository, never()).save(any());
        }

        @Test
        @DisplayName("falta el IMEI en una línea de CELULAR: 400")
        void sinImei_lanzaBadRequest() {
            ProductoMaster master = masterCelular();
            when(productoMasterRepository.findById(1)).thenReturn(Optional.of(master));

            VentaRequestDto request = ventaBase(List.of(lineaCelular(null)));

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("IMEI");
        }
    }

    // ── Crear — crédito ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("crear — venta a crédito")
    class CrearCredito {

        @Test
        @DisplayName("monto abonado menor al total sin cliente: 400, no se guarda nada")
        void sinCliente_lanzaBadRequest() {
            ProductoMaster master = masterAccesorio();
            Producto producto = productoAccesorio(master, BigDecimal.TEN);
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(producto));

            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 1)));
            request.setMontoAbonado(new BigDecimal("5")); // total es 15

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("requiere un cliente registrado");

            verify(ventaRepository, never()).save(any());
            // El stock no debe haberse tocado: la validación de crédito ocurre
            // antes de aplicar cualquier descuento.
            assertThat(producto.getStock()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("monto abonado menor al total CON cliente: se permite y se guarda tal cual")
        void conCliente_sePermite() {
            ProductoMaster master = masterAccesorio();
            Producto producto = productoAccesorio(master, BigDecimal.TEN);
            Cliente cliente = Cliente.builder().idcliente(7).nombreCompleto("María López").telefono("5512345678").build();

            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(producto));
            when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));

            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 1)));
            request.setIdcliente(7);
            request.setMontoAbonado(new BigDecimal("5"));

            VentaResponseDto response = ventaService.crear(request, ID_VENDEDOR);

            assertThat(response.getTotal()).isEqualByComparingTo("15");
            assertThat(response.getMontoAbonado()).isEqualByComparingTo("5");
            assertThat(response.getCambio()).isEqualByComparingTo("0"); // no hay cambio negativo
            assertThat(response.getNombreCliente()).isEqualTo("María López");

            // El saldo (15 - 5 = 10) queda como cuenta por cobrar, con el plazo por defecto (null).
            verify(cuentaPorCobrarService).crearDesdeVenta(any(Venta.class), eq(new BigDecimal("10")), isNull());
        }

        @Test
        @DisplayName("venta a crédito con fecha de vencimiento: se la pasa a la cuenta por cobrar")
        void conFechaDeVencimiento_sePasaALaCuenta() {
            ProductoMaster master = masterAccesorio();
            Producto producto = productoAccesorio(master, BigDecimal.TEN);
            Cliente cliente = Cliente.builder().idcliente(7).nombreCompleto("María López").build();
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(producto));
            when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));

            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 1)));
            request.setIdcliente(7);
            request.setMontoAbonado(new BigDecimal("5"));
            java.time.LocalDate vence = java.time.LocalDate.now().plusDays(15);
            request.setFechaVencimientoCredito(vence);

            ventaService.crear(request, ID_VENDEDOR);

            verify(cuentaPorCobrarService).crearDesdeVenta(any(Venta.class), eq(new BigDecimal("10")), eq(vence));
        }

        @Test
        @DisplayName("fecha de vencimiento anterior a hoy: 400 y no se guarda nada")
        void fechaDeVencimientoPasada_lanzaBadRequest() {
            ProductoMaster master = masterAccesorio();
            Producto producto = productoAccesorio(master, BigDecimal.TEN);
            Cliente cliente = Cliente.builder().idcliente(7).nombreCompleto("María López").build();
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(producto));
            when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));

            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 1)));
            request.setIdcliente(7);
            request.setMontoAbonado(new BigDecimal("5"));
            request.setFechaVencimientoCredito(java.time.LocalDate.now().minusDays(1));

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("anterior a hoy");
            verify(ventaRepository, never()).save(any());
            verify(cuentaPorCobrarService, never()).crearDesdeVenta(any(), any(), any());
        }

        @Test
        @DisplayName("venta pagada completa: no genera cuenta por cobrar")
        void pagoCompleto_noGeneraCuenta() {
            ProductoMaster master = masterAccesorio();
            Producto producto = productoAccesorio(master, BigDecimal.TEN);
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(producto));

            ventaService.crear(ventaBase(List.of(lineaAccesorio((short) 1))), ID_VENDEDOR);

            verify(cuentaPorCobrarService, never()).crearDesdeVenta(any(), any(), any());
        }
    }

    // ── Crear — regalos y costo por unidad ───────────────────────────────────

    @Nested
    @DisplayName("crear — regalos (precio $0)")
    class Regalo {

        private void accesorioEnStock() {
            ProductoMaster master = masterAccesorio();
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(productoAccesorio(master, BigDecimal.TEN)));
        }

        private VentaDetalleRequestDto regalo(String precio) {
            VentaDetalleRequestDto d = lineaAccesorio((short) 1);
            d.setPrecioUnitarioFinal(new BigDecimal(precio));
            d.setEsRegalo(true);
            return d;
        }

        private VentaDetalleRequestDto conPrecio(String precio) {
            VentaDetalleRequestDto d = lineaAccesorio((short) 1);
            d.setPrecioUnitarioFinal(new BigDecimal(precio));
            return d;
        }

        private void vendedorConRol(com.skycel.backend.domain.enums.Rol rol) {
            vendedor.setRol(rol);
        }

        @Test
        @DisplayName("un encargado puede regalar: la línea queda con precio $0, marcada como regalo, y el stock baja")
        void encargadoRegala() {
            accesorioEnStock();
            vendedorConRol(com.skycel.backend.domain.enums.Rol.ENCARGADO_TIENDA);

            VentaResponseDto r = ventaService.crear(
                    ventaBase(List.of(lineaAccesorio((short) 1), regalo("0"))), ID_VENDEDOR);

            assertThat(r.getTotal()).isEqualByComparingTo("15");        // el regalo no suma
            ArgumentCaptor<VentaDetalle> cap = ArgumentCaptor.forClass(VentaDetalle.class);
            verify(ventaDetalleRepository, times(2)).save(cap.capture());
            VentaDetalle regaloGuardado = cap.getAllValues().get(1);
            assertThat(regaloGuardado.getEsRegalo()).isTrue();
            assertThat(regaloGuardado.getPrecioUnitarioFinal()).isEqualByComparingTo("0");
            assertThat(regaloGuardado.getCostoUnitarioCompra()).isEqualByComparingTo("5"); // la pérdida queda registrada
        }

        @Test
        @DisplayName("un vendedor no puede regalar: 403")
        void vendedorNoRegala() {
            accesorioEnStock();
            vendedorConRol(com.skycel.backend.domain.enums.Rol.VENDEDOR);

            assertThatThrownBy(() -> ventaService.crear(
                    ventaBase(List.of(lineaAccesorio((short) 1), regalo("0"))), ID_VENDEDOR))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
            verify(ventaRepository, never()).save(any());
        }

        @Test
        @DisplayName("precio $0 sin marcarlo como regalo: 400")
        void precioCeroSinRegalo() {
            accesorioEnStock();

            assertThatThrownBy(() -> ventaService.crear(
                    ventaBase(List.of(lineaAccesorio((short) 1), conPrecio("0"))), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("solo se permite en artículos marcados como regalo");
        }

        @Test
        @DisplayName("un regalo con precio mayor a $0: 400")
        void regaloConPrecio() {
            accesorioEnStock();
            vendedorConRol(com.skycel.backend.domain.enums.Rol.ADMIN);

            assertThatThrownBy(() -> ventaService.crear(
                    ventaBase(List.of(lineaAccesorio((short) 1), regalo("10"))), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("debe tener precio $0");
        }

        @Test
        @DisplayName("una venta solo de regalos (total $0): 400")
        void ventaDeCero() {
            accesorioEnStock();
            vendedorConRol(com.skycel.backend.domain.enums.Rol.ADMIN);

            assertThatThrownBy(() -> ventaService.crear(ventaBase(List.of(regalo("0"))), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no puede ser de $0");
            verify(ventaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("crear — costo por unidad de un equipo")
    class CostoPorUnidad {

        private ProductoImei unidadConCosto(Producto producto, BigDecimal costo) {
            ProductoImei pi = ProductoImei.builder().id(1L).producto(producto).imei("350000000000011")
                    .estado("DISPONIBLE").costoUnitario(costo).build();
            when(productoImeiRepository.findByImei("350000000000011")).thenReturn(Optional.of(pi));
            return pi;
        }

        @Test
        @DisplayName("un usado con costo propio: el costo de la venta es el de esa unidad, no el del modelo")
        void usaElCostoDeLaUnidad() {
            ProductoMaster master = masterCelular();
            Producto producto = productoCelular(master, BigDecimal.ONE);   // costo del modelo: 100
            unidadConCosto(producto, new BigDecimal("80"));
            when(productoMasterRepository.findById(1)).thenReturn(Optional.of(master));

            ventaService.crear(ventaBase(List.of(lineaCelular("350000000000011"))), ID_VENDEDOR);

            ArgumentCaptor<VentaDetalle> cap = ArgumentCaptor.forClass(VentaDetalle.class);
            verify(ventaDetalleRepository).save(cap.capture());
            assertThat(cap.getValue().getCostoUnitarioCompra()).isEqualByComparingTo("80");
        }

        @Test
        @DisplayName("sin costo propio: se usa el costo del modelo")
        void usaElCostoDelModelo() {
            ProductoMaster master = masterCelular();
            Producto producto = productoCelular(master, BigDecimal.ONE);
            unidadConCosto(producto, null);
            when(productoMasterRepository.findById(1)).thenReturn(Optional.of(master));

            ventaService.crear(ventaBase(List.of(lineaCelular("350000000000011"))), ID_VENDEDOR);

            ArgumentCaptor<VentaDetalle> cap = ArgumentCaptor.forClass(VentaDetalle.class);
            verify(ventaDetalleRepository).save(cap.capture());
            assertThat(cap.getValue().getCostoUnitarioCompra()).isEqualByComparingTo("100");
        }
    }

    // ── Venta de una orden de servicio (con anticipo) ────────────────────────

    @Nested
    @DisplayName("venta de una orden de servicio: línea de pago ANTICIPO")
    class DesdeOrdenServicio {

        private com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto pago(int metodo, String monto) {
            var p = new com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto();
            p.setMetodoPago((byte) metodo);
            p.setMonto(new BigDecimal(monto));
            return p;
        }

        /** Venta de 4 accesorios a $15 = $60 con método Mixto y el desglose indicado. */
        private VentaRequestDto mixta(com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto... pagos) {
            ProductoMaster master = masterAccesorio();
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(productoAccesorio(master, BigDecimal.TEN)));
            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 4)));
            request.setMetodoPago((byte) 4);
            request.setPagos(List.of(pagos));
            return request;
        }

        @Test
        @DisplayName("anticipo + saldo en efectivo: la venta queda pagada y a la caja entra solo el efectivo del saldo")
        void anticipoMasSaldo() {
            VentaRequestDto request = mixta(pago(6, "20"), pago(1, "40"));

            VentaResponseDto r = ventaService.crearDesdeOrdenServicio(request, ID_VENDEDOR);

            assertThat(r.getMontoAbonado()).isEqualByComparingTo("60");
            verify(movimientoCajaService).registrarEntradaVenta(any(Venta.class), eq(vendedor),
                    argThat(m -> m.compareTo(new BigDecimal("40")) == 0));
            verify(cuentaPorCobrarService, never()).crearDesdeVenta(any(), any(), any());
        }

        @Test
        @DisplayName("si el anticipo cubre todo basta una línea, y a la caja no entra nada (ya entró al recibirlo)")
        void soloAnticipo() {
            VentaRequestDto request = mixta(pago(6, "60"));

            ventaService.crearDesdeOrdenServicio(request, ID_VENDEDOR);

            verify(movimientoCajaService).registrarEntradaVenta(any(Venta.class), eq(vendedor), argThat(m -> m.signum() == 0));
        }

        @Test
        @DisplayName("la línea ANTICIPO se muestra en el desglose de la respuesta como \"Anticipo\"")
        void seMuestraComoAnticipo() {
            when(ventaPagoDetalleRepository.findByVenta_IdventaOrderByIdpagoDetalle(500)).thenReturn(List.of(
                    VentaPagoDetalle.builder().metodoPago((byte) 6).monto(new BigDecimal("20")).build(),
                    VentaPagoDetalle.builder().metodoPago((byte) 1).monto(new BigDecimal("40")).build()));

            VentaResponseDto r = ventaService.crearDesdeOrdenServicio(mixta(pago(6, "20"), pago(1, "40")), ID_VENDEDOR);

            assertThat(r.getPagos()).extracting(p -> p.getDescripcionMetodoPago()).containsExactly("Anticipo", "Efectivo");
        }

        @Test
        @DisplayName("una venta normal (no de una orden) no admite líneas ANTICIPO: 400")
        void ventaNormalNoAdmiteAnticipo() {
            assertThatThrownBy(() -> ventaService.crear(mixta(pago(6, "20"), pago(1, "40")), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no válido");
            verify(ventaRepository, never()).save(any());
        }

        @Test
        @DisplayName("un regalo ya autorizado en la orden no se rechaza aunque quien entrega sea un vendedor")
        void regaloYaAutorizado() {
            vendedor.setRol(com.skycel.backend.domain.enums.Rol.VENDEDOR);
            ProductoMaster master = masterAccesorio();
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(productoAccesorio(master, BigDecimal.TEN)));
            VentaDetalleRequestDto regalo = lineaAccesorio((short) 1);
            regalo.setPrecioUnitarioFinal(BigDecimal.ZERO);
            regalo.setEsRegalo(true);
            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 1), regalo));

            assertThat(ventaService.crearDesdeOrdenServicio(request, ID_VENDEDOR).getTotal()).isEqualByComparingTo("15");
            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("una venta que salió de una orden de servicio no se cancela por separado: 409")
        void noSeCancelaPorSeparado() {
            Venta venta = Venta.builder().idventa(500).estado((byte) 1).build();
            when(ventaRepository.findById(500)).thenReturn(Optional.of(venta));
            when(ordenServicioRepository.existsByVenta_Idventa(500)).thenReturn(true);

            assertThatThrownBy(() -> ventaService.cancelar(500, ID_VENDEDOR))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                        assertThat(e.getReason()).contains("orden de servicio");
                    });
            assertThat(venta.getEstado()).isEqualTo((byte) 1);
            verifyNoInteractions(ventaDetalleRepository);
        }
    }

    // ── Crear — pago MIXTO ───────────────────────────────────────────────────

    @Nested
    @DisplayName("crear — pago Mixto")
    class PagoMixto {

        private com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto pago(int metodo, String monto, String folio) {
            var p = new com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto();
            p.setMetodoPago((byte) metodo);
            p.setMonto(new BigDecimal(monto));
            p.setFolioOperacion(folio);
            return p;
        }

        /** Venta de 4 accesorios a $15 = $60, con método Mixto y el desglose indicado. */
        private VentaRequestDto mixta(com.skycel.backend.dto.venta.VentaPagoDetalleRequestDto... pagos) {
            ProductoMaster master = masterAccesorio();
            Producto producto = productoAccesorio(master, BigDecimal.TEN);
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(producto));
            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 4)));
            request.setMetodoPago((byte) 4);
            request.setPagos(pagos.length == 0 ? null : List.of(pagos));
            return request;
        }

        private void conCliente(VentaRequestDto request) {
            Cliente cliente = Cliente.builder().idcliente(7).nombreCompleto("María López").build();
            when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));
            request.setIdcliente(7);
        }

        @Test
        @DisplayName("desglose completo: guarda cada línea, abona la suma y solo el efectivo entra a caja")
        void desgloseCompleto_soloElEfectivoEntraACaja() {
            VentaRequestDto request = mixta(pago(1, "40", null), pago(2, "20", "VOU-123"));
            when(ventaPagoDetalleRepository.findByVenta_IdventaOrderByIdpagoDetalle(500)).thenReturn(List.of(
                    VentaPagoDetalle.builder().metodoPago((byte) 1).monto(new BigDecimal("40")).build(),
                    VentaPagoDetalle.builder().metodoPago((byte) 2).monto(new BigDecimal("20")).folioOperacion("VOU-123").build()));

            VentaResponseDto response = ventaService.crear(request, ID_VENDEDOR);

            assertThat(response.getTotal()).isEqualByComparingTo("60");
            assertThat(response.getMontoAbonado()).isEqualByComparingTo("60");
            assertThat(response.getPagos()).hasSize(2);
            assertThat(response.getPagos().get(0).getDescripcionMetodoPago()).isEqualTo("Efectivo");
            assertThat(response.getPagos().get(1).getFolioOperacion()).isEqualTo("VOU-123");

            ArgumentCaptor<VentaPagoDetalle> cap = ArgumentCaptor.forClass(VentaPagoDetalle.class);
            verify(ventaPagoDetalleRepository, times(2)).save(cap.capture());
            assertThat(cap.getAllValues().get(0).getMonto()).isEqualByComparingTo("40");
            assertThat(cap.getAllValues().get(1).getMonto()).isEqualByComparingTo("20");
            assertThat(cap.getAllValues().get(1).getFolioOperacion()).isEqualTo("VOU-123");
            verify(movimientoCajaService).registrarEntradaVenta(any(Venta.class), eq(vendedor),
                    argThat(m -> m.compareTo(new BigDecimal("40")) == 0));
            verify(movimientoCajaService, never()).registrarVentaEfectivo(any(), any());
            verify(cuentaPorCobrarService, never()).crearDesdeVenta(any(), any(), any());
        }

        @Test
        @DisplayName("sin efectivo en el desglose (tarjeta + transferencia): no entra dinero a caja")
        void sinEfectivo_noEntraNadaACaja() {
            ventaService.crear(mixta(pago(2, "30", null), pago(3, "30", "SPEI-9")), ID_VENDEDOR);

            verify(movimientoCajaService).registrarEntradaVenta(any(Venta.class), eq(vendedor),
                    argThat(m -> m.signum() == 0));
        }

        @Test
        @DisplayName("desglose menor al total con cliente: es venta a crédito por el saldo")
        void sumaMenorAlTotal_conCliente_generaCuentaPorElSaldo() {
            VentaRequestDto request = mixta(pago(1, "30", null), pago(2, "10", null));
            conCliente(request);

            VentaResponseDto response = ventaService.crear(request, ID_VENDEDOR);

            assertThat(response.getMontoAbonado()).isEqualByComparingTo("40");
            verify(cuentaPorCobrarService).crearDesdeVenta(any(Venta.class), argThat(s -> s.compareTo(new BigDecimal("20")) == 0), isNull());
        }

        @Test
        @DisplayName("desglose menor al total sin cliente: 400")
        void sumaMenorAlTotal_sinCliente() {
            assertThatThrownBy(() -> ventaService.crear(mixta(pago(1, "30", null), pago(2, "10", null)), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("requiere un cliente registrado");
            verify(ventaRepository, never()).save(any());
        }

        @Test
        @DisplayName("desglose mayor al total: 400")
        void sumaMayorAlTotal() {
            assertThatThrownBy(() -> ventaService.crear(mixta(pago(1, "40", null), pago(2, "30", null)), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("supera el total");
            verify(ventaRepository, never()).save(any());
        }

        @Test
        @DisplayName("monto abonado que contradice la suma del desglose: 400")
        void montoAbonadoContradice() {
            VentaRequestDto request = mixta(pago(1, "40", null), pago(2, "20", null));
            request.setMontoAbonado(new BigDecimal("50"));

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no coincide");
        }

        @Test
        @DisplayName("método Mixto sin desglose: 400")
        void sinDesglose() {
            assertThatThrownBy(() -> ventaService.crear(mixta(), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("desglose");
        }

        @Test
        @DisplayName("desglose de una sola línea: 400")
        void unaSolaLinea() {
            assertThatThrownBy(() -> ventaService.crear(mixta(pago(1, "60", null)), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("al menos 2 líneas");
        }

        @Test
        @DisplayName("una línea con método no válido (p. ej. Mixto dentro del Mixto): 400")
        void metodoNoValidoEnLinea() {
            assertThatThrownBy(() -> ventaService.crear(mixta(pago(1, "30", null), pago(4, "30", null)), ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no válido");
        }

        @Test
        @DisplayName("desglose con un método que no es Mixto: 400")
        void desgloseConMetodoNoMixto() {
            VentaRequestDto request = mixta(pago(1, "30", null), pago(2, "30", null));
            request.setMetodoPago((byte) 1);

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("solo aplica al método Mixto");
        }
    }

    // ── Crear — ACCESORIO / validaciones generales ──────────────────────────

    @Nested
    @DisplayName("crear — validaciones generales")
    class Validaciones {

        @Test
        @DisplayName("stock insuficiente en ACCESORIO: 400")
        void stockInsuficiente_lanzaBadRequest() {
            ProductoMaster master = masterAccesorio();
            Producto producto = productoAccesorio(master, BigDecimal.ONE);
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(master));
            when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(2, CODTI))
                    .thenReturn(List.of(producto));

            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 5)));

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Stock insuficiente");
        }

        @Test
        @DisplayName("la caja no pertenece a la tienda indicada: 400")
        void cajaDeOtraTienda_lanzaBadRequest() {
            Tienda otraTienda = Tienda.builder().codti(2).nombre("Sucursal Norte").build();
            Caja cajaDeOtraTienda = Caja.builder().idCaja(ID_CAJA).tienda(otraTienda).nombreCaja("Caja Norte").build();
            when(cajaRepository.findById(ID_CAJA)).thenReturn(Optional.of(cajaDeOtraTienda));

            VentaRequestDto request = ventaBase(List.of(lineaAccesorio((short) 1)));

            assertThatThrownBy(() -> ventaService.crear(request, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no pertenece a la tienda");

            verifyNoInteractions(productoMasterRepository);
        }
    }

    // ── Cancelar ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelar")
    class Cancelar {

        @Test
        @DisplayName("revierte el IMEI a DISPONIBLE, suma 1 al stock y marca la venta como CANCELADA")
        void cancelarVentaDeCelular_revierteImeiYStock() {
            ProductoMaster master = masterCelular();
            Producto producto = productoCelular(master, BigDecimal.ZERO); // ya vendido, stock en 0
            Venta venta = Venta.builder()
                    .idventa(500).estado((byte) 1).total(new BigDecimal("199"))
                    .tienda(tienda).caja(caja).usuarioVendedor(vendedor)
                    .montoAbonado(new BigDecimal("199"))
                    .build();
            VentaDetalle detalle = VentaDetalle.builder()
                    .iddetalleVenta(900).venta(venta).productoMaster(master)
                    .codpro(producto.getCodpro()).cantidad((short) 1)
                    .precioUnitarioBase(new BigDecimal("200")).precioUnitarioFinal(new BigDecimal("199"))
                    .costoUnitarioCompra(new BigDecimal("100")).imei("123456789012345")
                    .build();
            ProductoImei pi = ProductoImei.builder().id(1L).producto(producto).imei("123456789012345").estado("VENDIDO").build();

            when(ventaRepository.findById(500)).thenReturn(Optional.of(venta));
            when(ventaDetalleRepository.findByVenta_Idventa(500)).thenReturn(List.of(detalle));
            when(productoImeiRepository.findByImei("123456789012345")).thenReturn(Optional.of(pi));

            ventaService.cancelar(500, ID_VENDEDOR);

            assertThat(pi.getEstado()).isEqualTo("DISPONIBLE");
            assertThat(producto.getStock()).isEqualByComparingTo("1");
            assertThat(venta.getEstado()).isEqualTo((byte) 2);
        }

        @Test
        @DisplayName("cancelar una venta ya cancelada: 400")
        void yaCancelada_lanzaBadRequest() {
            Venta venta = Venta.builder().idventa(500).estado((byte) 2).build();
            when(ventaRepository.findById(500)).thenReturn(Optional.of(venta));

            assertThatThrownBy(() -> ventaService.cancelar(500, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("ya está cancelada");

            verifyNoInteractions(ventaDetalleRepository);
        }

        @Test
        @DisplayName("cancelar consulta la cuenta por cobrar de la venta; si tiene abonos (409) no se revierte nada")
        void conAbonosEnLaCuenta_bloqueaLaCancelacion() {
            Venta venta = Venta.builder().idventa(500).estado((byte) 1).build();
            when(ventaRepository.findById(500)).thenReturn(Optional.of(venta));
            doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "tiene abonos"))
                    .when(cuentaPorCobrarService).cancelarPorVenta(500);

            assertThatThrownBy(() -> ventaService.cancelar(500, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("tiene abonos");

            assertThat(venta.getEstado()).isEqualTo((byte) 1);          // sigue completada
            verifyNoInteractions(ventaDetalleRepository);                // no se tocó stock ni IMEIs
            verify(movimientoCajaService, never()).registrarCancelacionVenta(any(), any());
        }

        @Test
        @DisplayName("venta inexistente: 404")
        void ventaInexistente_lanza404() {
            when(ventaRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ventaService.cancelar(999, ID_VENDEDOR))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no encontrada");
        }
    }
}
