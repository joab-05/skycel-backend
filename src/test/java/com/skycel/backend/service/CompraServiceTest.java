package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ProductoUpdateDTO;
import com.skycel.backend.domain.dto.request.StockAjusteDTO;
import com.skycel.backend.domain.dto.request.UnidadEquipoDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.compra.CompraLineaRequestDto;
import com.skycel.backend.dto.compra.CompraRequestDto;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de compras a proveedor y su cuenta por pagar (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class CompraServiceTest {

    @Mock private CompraRepository      compraRepository;
    @Mock private PagoCompraRepository  pagoRepository;
    @Mock private ProveedorRepository   proveedorRepository;
    @Mock private TiendaRepository      tiendaRepository;
    @Mock private ProductoRepository    productoRepository;
    @Mock private UsuarioRepository     usuarioRepository;
    @Mock private ProductoService       productoService;
    @Mock private MovimientoCajaService movimientoCajaService;

    @InjectMocks
    private CompraService service;

    private Usuario admin;
    private Usuario encargadoTienda2;
    private Tienda tienda2;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        tienda2 = Tienda.builder().codti(2).nombre("Zócalo").build();
        Tienda tienda1 = Tienda.builder().codti(1).nombre("Matriz").build();
        admin = Usuario.builder().idusuario(1).username("admin").rol(Rol.ADMIN).nombreCompleto("Root").build();
        encargadoTienda2 = Usuario.builder().idusuario(2).username("enc2").rol(Rol.ENCARGADO_TIENDA).tienda(tienda2).nombreCompleto("Encargado").build();
        proveedor = Proveedor.builder().idproveedor((short) 5).nombreFiscal("Proveedor SA").nombreCorto("ProveSA")
                .diasCredito(15).activo(true).build();

        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("enc2")).thenReturn(Optional.of(encargadoTienda2));
        lenient().when(proveedorRepository.findById((short) 5)).thenReturn(Optional.of(proveedor));
        lenient().when(tiendaRepository.findById(2)).thenReturn(Optional.of(tienda2));
        lenient().when(tiendaRepository.findById(1)).thenReturn(Optional.of(tienda1));
        lenient().when(compraRepository.save(any(Compra.class))).thenAnswer(i -> {
            Compra c = i.getArgument(0);
            if (c.getIdcompra() == null) c.setIdcompra(7);
            return c;
        });
    }

    private Producto accesorio(String codpro, int codti) {
        ProductoMaster master = ProductoMaster.builder().idprodmaster(1).tipo(TipoProducto.ACCESORIO).nombreBase("Cable USB-C").build();
        return Producto.builder().idproducto(100).codpro(codpro).tienda(codti == 2 ? tienda2 : Tienda.builder().codti(codti).build())
                .productoMaster(master).stock(new BigDecimal("10")).build();
    }

    private Producto equipo(String codpro, int codti) {
        ProductoMaster master = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.CELULAR).nombreBase("Redmi 13C").build();
        return Producto.builder().idproducto(101).codpro(codpro).tienda(tienda2)
                .productoMaster(master).stock(BigDecimal.ZERO).build();
    }

    private Producto servicio(String codpro, int codti) {
        ProductoMaster master = ProductoMaster.builder().idprodmaster(3).tipo(TipoProducto.SERVICIO).nombreBase("Cambio de pantalla").build();
        return Producto.builder().idproducto(102).codpro(codpro).tienda(tienda2).productoMaster(master).build();
    }

    private CompraRequestDto dtoAccesorio() {
        CompraRequestDto dto = new CompraRequestDto();
        dto.setIdProveedor((short) 5);
        dto.setCodti(2);
        CompraLineaRequestDto linea = new CompraLineaRequestDto();
        linea.setCodpro("CAB-001");
        linea.setCantidad(new BigDecimal("20"));
        linea.setCostoUnitario(new BigDecimal("10.00"));
        dto.setLineas(List.of(linea));
        return dto;
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear: accesorio suma stock, actualiza el costo y calcula el total")
    void crear_accesorio() {
        when(productoRepository.findByCodproAndTienda_Codti("CAB-001", 2)).thenReturn(Optional.of(accesorio("CAB-001", 2)));

        Map<String, Object> r = service.crear(dtoAccesorio(), "admin");

        ArgumentCaptor<StockAjusteDTO> ajusteCap = ArgumentCaptor.forClass(StockAjusteDTO.class);
        verify(productoService).ajustarStock(eq("CAB-001"), eq(2), ajusteCap.capture());
        assertThat(ajusteCap.getValue().getTipo()).isEqualTo("ENTRADA");
        assertThat(ajusteCap.getValue().getCantidad()).isEqualByComparingTo("20");

        ArgumentCaptor<ProductoUpdateDTO> costoCap = ArgumentCaptor.forClass(ProductoUpdateDTO.class);
        verify(productoService).actualizar(eq("CAB-001"), eq(2), costoCap.capture());
        assertThat(costoCap.getValue().getPrecioCompra()).isEqualByComparingTo("10.00");

        assertThat(r.get("montoTotal")).isEqualTo(new BigDecimal("200.00"));
        assertThat(r.get("estado")).isEqualTo((byte) 0);
        assertThat(r.get("estadoDisplay")).isEqualTo("Pendiente");
    }

    @Test
    @DisplayName("crear: la fecha de vencimiento se calcula con los días de crédito del proveedor")
    void crear_fechaVencimientoPorDiasCredito() {
        when(productoRepository.findByCodproAndTienda_Codti("CAB-001", 2)).thenReturn(Optional.of(accesorio("CAB-001", 2)));

        service.crear(dtoAccesorio(), "admin");

        ArgumentCaptor<Compra> cap = ArgumentCaptor.forClass(Compra.class);
        verify(compraRepository, atLeastOnce()).save(cap.capture());
        assertThat(cap.getValue().getFechaVencimiento()).isEqualTo(LocalDate.now().plusDays(15));
    }

    @Test
    @DisplayName("crear: equipo con IMEI manda las unidades y NO toca el costo del modelo")
    void crear_equipoConImeis() {
        when(productoRepository.findByCodproAndTienda_Codti("CEL-001", 2)).thenReturn(Optional.of(equipo("CEL-001", 2)));
        CompraRequestDto dto = new CompraRequestDto();
        dto.setIdProveedor((short) 5); dto.setCodti(2);
        CompraLineaRequestDto linea = new CompraLineaRequestDto();
        linea.setCodpro("CEL-001");
        UnidadEquipoDTO u1 = new UnidadEquipoDTO(); u1.setImei("111111111111111"); u1.setCostoUnitario(new BigDecimal("1500"));
        UnidadEquipoDTO u2 = new UnidadEquipoDTO(); u2.setImei("222222222222222"); u2.setCostoUnitario(new BigDecimal("1600"));
        linea.setUnidades(List.of(u1, u2));
        dto.setLineas(List.of(linea));

        Map<String, Object> r = service.crear(dto, "admin");

        ArgumentCaptor<StockAjusteDTO> cap = ArgumentCaptor.forClass(StockAjusteDTO.class);
        verify(productoService).ajustarStock(eq("CEL-001"), eq(2), cap.capture());
        assertThat(cap.getValue().getCantidad()).isEqualByComparingTo("2");
        assertThat(cap.getValue().getUnidades()).hasSize(2);
        assertThat(u1.getCondicion()).isEqualTo("NUEVO"); // se completa si no se manda
        verify(productoService, never()).actualizar(any(), any(), any());
        assertThat(r.get("montoTotal")).isEqualTo(new BigDecimal("3100"));
    }

    @Test
    @DisplayName("crear: un servicio no se puede comprar (no maneja inventario)")
    void crear_servicio_rechaza() {
        when(productoRepository.findByCodproAndTienda_Codti("SRV-001", 2)).thenReturn(Optional.of(servicio("SRV-001", 2)));
        CompraRequestDto dto = new CompraRequestDto();
        dto.setIdProveedor((short) 5); dto.setCodti(2);
        CompraLineaRequestDto linea = new CompraLineaRequestDto();
        linea.setCodpro("SRV-001"); linea.setCantidad(BigDecimal.ONE); linea.setCostoUnitario(BigDecimal.TEN);
        dto.setLineas(List.of(linea));

        assertThatThrownBy(() -> service.crear(dto, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(productoService);
    }

    @Test
    @DisplayName("crear: proveedor desactivado -> 400")
    void crear_proveedorInactivo() {
        proveedor.setActivo(false);

        assertThatThrownBy(() -> service.crear(dtoAccesorio(), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(compraRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: un encargado solo registra compras de su propia tienda")
    void crear_encargadoOtraTienda_rechaza() {
        CompraRequestDto dto = dtoAccesorio();
        dto.setCodti(1); // encargadoTienda2 es de la tienda 2

        assertThatThrownBy(() -> service.crear(dto, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(productoService);
    }

    @Test
    @DisplayName("crear: un encargado sí registra compras de su propia tienda")
    void crear_encargadoSuPropiaTienda_ok() {
        when(productoRepository.findByCodproAndTienda_Codti("CAB-001", 2)).thenReturn(Optional.of(accesorio("CAB-001", 2)));

        service.crear(dtoAccesorio(), "enc2");

        verify(productoService).ajustarStock(eq("CAB-001"), eq(2), any());
    }

    // ── Pago ──────────────────────────────────────────────────────────────────

    private Compra compraConSaldo() {
        Compra c = Compra.builder().idcompra(7).proveedor(proveedor).tienda(tienda2)
                .montoTotal(new BigDecimal("300.00")).montoPagado(BigDecimal.ZERO)
                .fechaVencimiento(LocalDate.now().plusDays(10)).estado((byte) 0).build();
        lenient().when(compraRepository.findById(7)).thenReturn(Optional.of(c));
        lenient().when(pagoRepository.save(any(PagoCompra.class))).thenAnswer(i -> {
            PagoCompra p = i.getArgument(0);
            p.setIdpago(50);
            return p;
        });
        return c;
    }

    @Test
    @DisplayName("pago en efectivo: sale de la caja de la tienda de la compra y actualiza el saldo")
    void pagoEfectivo_mueveCaja() {
        Compra compra = compraConSaldo();

        Map<String, Object> r = service.registrarPago(7, new BigDecimal("100.00"), (byte) 1, "primer abono", "admin", 3);

        ArgumentCaptor<PagoCompra> cap = ArgumentCaptor.forClass(PagoCompra.class);
        verify(movimientoCajaService).registrarPagoCompra(eq(3), eq(compra), cap.capture(), eq(admin));
        assertThat(cap.getValue().getIdpago()).isEqualTo(50);
        assertThat(compra.getMontoPagado()).isEqualByComparingTo("100.00");
        assertThat(r.get("estado")).isEqualTo((byte) 1); // parcial
        assertThat(r.get("saldoPendiente")).isEqualTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("pago por transferencia: no mueve caja")
    void pagoTransferencia_noMueveCaja() {
        compraConSaldo();

        service.registrarPago(7, new BigDecimal("300.00"), (byte) 3, null, "admin", null);

        verifyNoInteractions(movimientoCajaService);
    }

    @Test
    @DisplayName("pago que salda el total: la compra queda Pagada")
    void pagoCompleto_quedaPagada() {
        compraConSaldo();

        Map<String, Object> r = service.registrarPago(7, new BigDecimal("300.00"), (byte) 1, null, "admin", null);

        assertThat(r.get("estado")).isEqualTo((byte) 2);
        assertThat(r.get("estadoDisplay")).isEqualTo("Pagada");
    }

    @Test
    @DisplayName("pago mayor al saldo pendiente -> 400")
    void pagoMayorAlSaldo_rechaza() {
        compraConSaldo();

        assertThatThrownBy(() -> service.registrarPago(7, new BigDecimal("500.00"), (byte) 1, null, "admin", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(pagoRepository, never()).save(any());
    }

    @Test
    @DisplayName("pago: un encargado no paga las compras de otra tienda")
    void pago_encargadoOtraTienda_rechaza() {
        Compra c = Compra.builder().idcompra(9).proveedor(proveedor).tienda(Tienda.builder().codti(1).nombre("Matriz").build())
                .montoTotal(new BigDecimal("50")).montoPagado(BigDecimal.ZERO).fechaVencimiento(LocalDate.now().plusDays(5)).estado((byte) 0).build();
        when(compraRepository.findById(9)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.registrarPago(9, new BigDecimal("10"), (byte) 1, null, "enc2", null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
