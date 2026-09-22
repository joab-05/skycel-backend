package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Cliente;
import com.skycel.backend.domain.entity.CuentaPorCobrar;
import com.skycel.backend.domain.entity.PagoCuenta;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.entity.Venta;
import com.skycel.backend.dto.cpc.CuentaPorCobrarRequestDto;
import com.skycel.backend.repository.ClienteRepository;
import com.skycel.backend.repository.CuentaPorCobrarRepository;
import com.skycel.backend.repository.PagoCuentaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.repository.VentaRepository;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de las cuentas por cobrar ligadas a ventas (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class CuentaPorCobrarServiceTest {

    @Mock private CuentaPorCobrarRepository cpcRepository;
    @Mock private PagoCuentaRepository      pagoRepository;
    @Mock private ClienteRepository         clienteRepository;
    @Mock private UsuarioRepository         usuarioRepository;
    @Mock private VentaRepository           ventaRepository;
    @Mock private MovimientoCajaService     movimientoCajaService;

    @InjectMocks
    private CuentaPorCobrarService service;

    private Cliente cliente;
    private Venta venta;

    @BeforeEach
    void setUp() {
        cliente = Cliente.builder().idcliente(4).nombreCompleto("Ana Torres").build();
        venta = Venta.builder().idventa(12).cliente(cliente)
                .total(new BigDecimal("199.00")).montoAbonado(new BigDecimal("100.00")).build();
        lenient().when(cpcRepository.save(any(CuentaPorCobrar.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("crearDesdeVenta: folio V-######, solo el saldo, ligada a la venta y a 30 días por defecto")
    void crearDesdeVenta_valoresPorDefecto() {
        CuentaPorCobrar c = service.crearDesdeVenta(venta, new BigDecimal("99.00"), null);

        assertThat(c.getNoFactura()).isEqualTo("V-000012");
        assertThat(c.getVenta()).isSameAs(venta);
        assertThat(c.getCliente()).isSameAs(cliente);
        assertThat(c.getMontoTotal()).isEqualByComparingTo("99.00");
        assertThat(c.getMontoPagado()).isEqualByComparingTo("0");
        assertThat(c.getEstado()).isEqualTo((byte) 0);
        assertThat(c.getFechaVencimiento()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(c.getObservaciones()).contains("venta #12");
    }

    @Test
    @DisplayName("crearDesdeVenta: respeta la fecha de vencimiento indicada")
    void crearDesdeVenta_conFecha() {
        LocalDate vence = LocalDate.now().plusDays(7);
        assertThat(service.crearDesdeVenta(venta, new BigDecimal("99.00"), vence).getFechaVencimiento()).isEqualTo(vence);
    }

    @Test
    @DisplayName("crearDesdeVenta: si el folio ya existe, 409")
    void crearDesdeVenta_folioDuplicado() {
        when(cpcRepository.existsByNoFactura("V-000012")).thenReturn(true);

        assertThatThrownBy(() -> service.crearDesdeVenta(venta, new BigDecimal("99.00"), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(cpcRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelarPorVenta: cuenta sin abonos -> se elimina")
    void cancelarPorVenta_sinAbonos() {
        CuentaPorCobrar cuenta = CuentaPorCobrar.builder().noFactura("V-000012").montoPagado(BigDecimal.ZERO).build();
        when(cpcRepository.findByVenta_Idventa(12)).thenReturn(Optional.of(cuenta));

        service.cancelarPorVenta(12);

        verify(cpcRepository).delete(cuenta);
    }

    @Test
    @DisplayName("cancelarPorVenta: cuenta con abonos -> 409 y no se elimina")
    void cancelarPorVenta_conAbonos() {
        CuentaPorCobrar cuenta = CuentaPorCobrar.builder().noFactura("V-000012").montoPagado(new BigDecimal("40.00")).build();
        when(cpcRepository.findByVenta_Idventa(12)).thenReturn(Optional.of(cuenta));

        assertThatThrownBy(() -> service.cancelarPorVenta(12))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("V-000012");
                });
        verify(cpcRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancelarPorVenta: venta sin cuenta (pagada completa) -> no hace nada")
    void cancelarPorVenta_sinCuenta() {
        when(cpcRepository.findByVenta_Idventa(12)).thenReturn(Optional.empty());

        service.cancelarPorVenta(12);

        verify(cpcRepository, never()).delete(any());
    }

    @Test
    @DisplayName("crear manual con idventa: queda ligada a la venta")
    void crearManual_ligaLaVenta() {
        when(clienteRepository.findById(4)).thenReturn(Optional.of(cliente));
        when(ventaRepository.findById(12)).thenReturn(Optional.of(venta));
        when(cpcRepository.findByVenta_Idventa(12)).thenReturn(Optional.empty());

        service.crear(dtoManual(4, 12));

        ArgumentCaptor<CuentaPorCobrar> cap = ArgumentCaptor.forClass(CuentaPorCobrar.class);
        verify(cpcRepository).save(cap.capture());
        assertThat(cap.getValue().getVenta()).isSameAs(venta);
    }

    @Test
    @DisplayName("crear manual: venta de otro cliente -> 400")
    void crearManual_ventaDeOtroCliente() {
        Cliente otro = Cliente.builder().idcliente(9).nombreCompleto("Otro").build();
        when(clienteRepository.findById(9)).thenReturn(Optional.of(otro));
        when(ventaRepository.findById(12)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> service.crear(dtoManual(9, 12)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("otro cliente");
        verify(cpcRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear manual: la venta ya tiene cuenta -> 409")
    void crearManual_ventaYaTieneCuenta() {
        when(clienteRepository.findById(4)).thenReturn(Optional.of(cliente));
        when(ventaRepository.findById(12)).thenReturn(Optional.of(venta));
        when(cpcRepository.findByVenta_Idventa(12)).thenReturn(Optional.of(new CuentaPorCobrar()));

        assertThatThrownBy(() -> service.crear(dtoManual(4, 12)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ── Abonos y caja ────────────────────────────────────────────────────────

    private CuentaPorCobrar cuentaConSaldo() {
        CuentaPorCobrar cuenta = CuentaPorCobrar.builder().idcuenta(5).noFactura("V-000009").cliente(cliente)
                .montoTotal(new BigDecimal("149.00")).montoPagado(BigDecimal.ZERO)
                .fechaVencimiento(LocalDate.now().plusDays(20)).estado((byte) 0).build();
        Usuario usuario = Usuario.builder().idusuario(4).build();
        lenient().when(cpcRepository.findById(5)).thenReturn(Optional.of(cuenta));
        lenient().when(usuarioRepository.findById(4)).thenReturn(Optional.of(usuario));
        lenient().when(pagoRepository.save(any(PagoCuenta.class))).thenAnswer(i -> {
            PagoCuenta p = i.getArgument(0);
            p.setIdpago(77);
            return p;
        });
        return cuenta;
    }

    @Test
    @DisplayName("abono en efectivo: entra a la caja indicada, con el pago ya guardado (tiene id)")
    void abonoEfectivo_entraALaCaja() {
        CuentaPorCobrar cuenta = cuentaConSaldo();

        service.registrarPago(5, new BigDecimal("30.00"), (byte) 1, null, 4, 3);

        ArgumentCaptor<PagoCuenta> cap = ArgumentCaptor.forClass(PagoCuenta.class);
        verify(movimientoCajaService).registrarAbonoCuenta(eq(3), eq(cuenta), cap.capture(), any(Usuario.class), isNull());
        assertThat(cap.getValue().getIdpago()).isEqualTo(77);
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("30.00");
        assertThat(cuenta.getMontoPagado()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("abono en efectivo sin idCaja: se delega la caja por defecto (idCaja null)")
    void abonoEfectivo_sinCaja_delegaLaCajaPorDefecto() {
        cuentaConSaldo();

        service.registrarPago(5, new BigDecimal("30.00"), (byte) 1, null, 4, null);

        verify(movimientoCajaService).registrarAbonoCuenta(isNull(), any(CuentaPorCobrar.class), any(PagoCuenta.class), any(Usuario.class), isNull());
    }

    @Test
    @DisplayName("abono por transferencia: no toca la caja")
    void abonoTransferencia_noMueveCaja() {
        cuentaConSaldo();

        service.registrarPago(5, new BigDecimal("30.00"), (byte) 3, null, 4, null);

        verifyNoInteractions(movimientoCajaService);
    }

    @Test
    @DisplayName("abono sin conexión: conserva la fecha real del pago, no la de sincronización")
    void abonoSinConexion_conservaFecha() {
        cuentaConSaldo();
        LocalDateTime fechaReal = LocalDateTime.now().minusHours(3).withNano(0);

        service.registrarPago(5, new BigDecimal("30.00"), (byte) 1, null, 4, 3, "clave-1", fechaReal);

        ArgumentCaptor<PagoCuenta> cap = ArgumentCaptor.forClass(PagoCuenta.class);
        verify(movimientoCajaService).registrarAbonoCuenta(eq(3), any(CuentaPorCobrar.class), cap.capture(), any(Usuario.class), eq(fechaReal));
        assertThat(cap.getValue().getFechaPago()).isEqualTo(fechaReal);
        assertThat(cap.getValue().getClaveOffline()).isEqualTo("clave-1");
    }

    @Test
    @DisplayName("abono sin conexión: reenviar la misma clave no duplica el pago")
    void abonoSinConexion_idempotente() {
        CuentaPorCobrar cuenta = cuentaConSaldo();
        LocalDateTime fechaReal = LocalDateTime.now().minusHours(1).withNano(0);
        PagoCuenta yaGuardado = PagoCuenta.builder().idpago(77).cuentaPorCobrar(cuenta).claveOffline("clave-2").build();
        when(pagoRepository.findByClaveOffline("clave-2")).thenReturn(Optional.of(yaGuardado));

        service.registrarPago(5, new BigDecimal("30.00"), (byte) 1, null, 4, 3, "clave-2", fechaReal);

        verify(pagoRepository, never()).save(any(PagoCuenta.class));
        verifyNoInteractions(movimientoCajaService);
    }

    @Test
    @DisplayName("abono sin conexión: rechaza fechas futuras y de hace más de 45 días")
    void abonoSinConexion_fechas() {
        cuentaConSaldo();

        assertThatThrownBy(() -> service.registrarPago(5, new BigDecimal("10.00"), (byte) 1, null, 4, 3,
                "clave-3", LocalDateTime.now().plusHours(2))).hasMessageContaining("futura");

        assertThatThrownBy(() -> service.registrarPago(5, new BigDecimal("10.00"), (byte) 1, null, 4, 3,
                "clave-4", LocalDateTime.now().minusDays(46))).hasMessageContaining("45 días");
    }

    private CuentaPorCobrarRequestDto dtoManual(Integer idcliente, Integer idventa) {
        CuentaPorCobrarRequestDto dto = new CuentaPorCobrarRequestDto();
        dto.setNoFactura("F-MANUAL-1");
        dto.setIdcliente(idcliente);
        dto.setIdventa(idventa);
        dto.setFechaVencimiento(LocalDate.now().plusDays(10));
        dto.setMontoTotal(new BigDecimal("50.00"));
        return dto;
    }
}
