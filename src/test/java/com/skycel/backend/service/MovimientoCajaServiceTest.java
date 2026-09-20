package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.caja.MovimientoCajaRequestDto;
import com.skycel.backend.dto.caja.MovimientoCajaResponseDto;
import com.skycel.backend.repository.CajaRepository;
import com.skycel.backend.repository.CatMotivoRepository;
import com.skycel.backend.repository.MovimientoCajaRepository;
import com.skycel.backend.repository.UsuarioRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de MovimientoCajaService (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class MovimientoCajaServiceTest {

    @Mock private MovimientoCajaRepository movimientoCajaRepository;
    @Mock private CajaRepository           cajaRepository;
    @Mock private CatMotivoRepository      catMotivoRepository;
    @Mock private UsuarioRepository        usuarioRepository;
    @Mock private com.skycel.backend.security.SesionActual sesionActual;

    @InjectMocks
    private MovimientoCajaService service;

    private static final Integer ID_CAJA = 1;

    private Tienda tienda;
    private Tienda otraTienda;
    private Caja caja;
    private Usuario encargado;
    private Usuario admin;
    private CatMotivo gasto;

    @BeforeEach
    void setUp() {
        tienda = Tienda.builder().codti(2).nombre("Zocalo").build();
        otraTienda = Tienda.builder().codti(3).nombre("Corpo").build();
        caja = Caja.builder().idCaja(ID_CAJA).nombreCaja("Caja Zocalo").tienda(tienda).build();
        encargado = Usuario.builder().idusuario(4).username("abigail").nombreCompleto("Abigail")
                .rol(Rol.ENCARGADO_TIENDA).tienda(tienda).build();
        admin = Usuario.builder().idusuario(1).username("root").nombreCompleto("Root")
                .rol(Rol.ROOT).tienda(tienda).build();
        gasto = CatMotivo.builder().idmotivo(5).nombre("Gasto operativo").tipoMov((byte) 2).catSat((byte) 1).activo(true).build();

        lenient().when(cajaRepository.findById(ID_CAJA)).thenReturn(Optional.of(caja));
        lenient().when(usuarioRepository.findByUsername("abigail")).thenReturn(Optional.of(encargado));
        lenient().when(usuarioRepository.findByUsername("root")).thenReturn(Optional.of(admin));
        lenient().when(movimientoCajaRepository.save(any(MovimientoCaja.class))).thenAnswer(i -> i.getArgument(0));
        saldo(new BigDecimal("500.00"), BigDecimal.ZERO);
    }

    private void saldo(BigDecimal entradas, BigDecimal salidas) {
        lenient().when(movimientoCajaRepository.totalPorTipo(ID_CAJA, (byte) 1)).thenReturn(entradas);
        lenient().when(movimientoCajaRepository.totalPorTipo(ID_CAJA, (byte) 2)).thenReturn(salidas);
    }

    private MovimientoCajaRequestDto req(Integer idmotivo, String monto) {
        MovimientoCajaRequestDto dto = new MovimientoCajaRequestDto();
        dto.setIdmotivo(idmotivo);
        dto.setMonto(new BigDecimal(monto));
        dto.setObservaciones("detalle");
        return dto;
    }

    private void motivo(CatMotivo m) {
        lenient().when(catMotivoRepository.findById(m.getIdmotivo())).thenReturn(Optional.of(m));
    }

    @Test
    @DisplayName("salida dentro del saldo: registra con el tipo del motivo y las observaciones")
    void salidaConSaldoSuficiente() {
        motivo(gasto);

        MovimientoCajaResponseDto r = service.registrarManual(ID_CAJA, req(5, "120.00"), "abigail");

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getTipo()).isEqualTo((byte) 2);
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("120.00");
        assertThat(cap.getValue().getObservaciones()).isEqualTo("detalle");
        assertThat(cap.getValue().getUsuarioAdmin()).isNull();
        assertThat(r.getTipoDisplay()).isEqualTo("Salida");
    }

    @Test
    @DisplayName("el movimiento guarda la sesión desde la que se hizo (y queda vacía si no hay sesión)")
    void guardaLaSesion() {
        motivo(gasto);
        com.skycel.backend.domain.entity.UsuarioSesion sesion = new com.skycel.backend.domain.entity.UsuarioSesion();
        sesion.setIdsesion(77);
        org.mockito.Mockito.when(sesionActual.actual()).thenReturn(sesion, (com.skycel.backend.domain.entity.UsuarioSesion) null);

        service.registrarManual(ID_CAJA, req(5, "10.00"), "abigail");
        service.registrarManual(ID_CAJA, req(5, "10.00"), "abigail");

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        assertThat(cap.getAllValues().get(0).getSesion().getIdsesion()).isEqualTo(77);
        assertThat(cap.getAllValues().get(1).getSesion()).isNull();
    }

    @Test
    @DisplayName("salida mayor al saldo: 400 y no guarda nada")
    void salidaSinSaldo() {
        motivo(gasto);

        assertThatThrownBy(() -> service.registrarManual(ID_CAJA, req(5, "900.00"), "abigail"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Saldo insuficiente");
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("motivo del sistema (Venta): no se puede capturar a mano")
    void motivoDelSistemaRechazado() {
        CatMotivo venta = CatMotivo.builder().idmotivo(1).nombre("Venta").tipoMov((byte) 1).catSat((byte) 1).activo(true).build();
        motivo(venta);

        assertThatThrownBy(() -> service.registrarManual(ID_CAJA, req(1, "10.00"), "abigail"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("automáticamente");
    }

    @Test
    @DisplayName("motivo desactivado: 400")
    void motivoInactivoRechazado() {
        gasto.setActivo(false);
        motivo(gasto);

        assertThatThrownBy(() -> service.registrarManual(ID_CAJA, req(5, "10.00"), "abigail"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("desactivado");
    }

    @Test
    @DisplayName("movimiento Personal: un encargado recibe 403; un ROOT lo autoriza y queda como admin")
    void movimientoPersonalSoloAdmin() {
        CatMotivo retiro = CatMotivo.builder().idmotivo(8).nombre("Retiro de dueño").tipoMov((byte) 2).catSat((byte) 5).activo(true).build();
        motivo(retiro);

        assertThatThrownBy(() -> service.registrarManual(ID_CAJA, req(8, "50.00"), "abigail"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        service.registrarManual(ID_CAJA, req(8, "50.00"), "root");
        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getUsuarioAdmin()).isSameAs(admin);
    }

    @Test
    @DisplayName("un encargado no puede operar la caja de otra tienda: 403")
    void cajaDeOtraTienda() {
        caja.setTienda(otraTienda);
        motivo(gasto);

        assertThatThrownBy(() -> service.registrarManual(ID_CAJA, req(5, "10.00"), "abigail"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("venta en efectivo con crédito: entra solo lo abonado")
    void ventaEfectivoACredito() {
        CatMotivo motivoVenta = CatMotivo.builder().idmotivo(1).nombre("Venta").tipoMov((byte) 1).catSat((byte) 1).activo(true).build();
        when(catMotivoRepository.findByNombre("Venta")).thenReturn(Optional.of(motivoVenta));
        Venta venta = Venta.builder().idventa(7).caja(caja).total(new BigDecimal("199.00"))
                .montoAbonado(new BigDecimal("100.00")).build();

        service.registrarVentaEfectivo(venta, encargado);

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("100.00");
        assertThat(cap.getValue().getTipo()).isEqualTo((byte) 1);
        assertThat(cap.getValue().getObservaciones()).isEqualTo("Venta #7");
    }

    @Test
    @DisplayName("venta con cambio: entra el total, no lo entregado por el cliente")
    void ventaEfectivoConCambio() {
        CatMotivo motivoVenta = CatMotivo.builder().idmotivo(1).nombre("Venta").tipoMov((byte) 1).catSat((byte) 1).activo(true).build();
        when(catMotivoRepository.findByNombre("Venta")).thenReturn(Optional.of(motivoVenta));
        Venta venta = Venta.builder().idventa(8).caja(caja).total(new BigDecimal("17397.00"))
                .montoAbonado(new BigDecimal("18000.00")).build();

        service.registrarVentaEfectivo(venta, encargado);

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("17397.00");
    }

    @Test
    @DisplayName("cancelar una venta con movimiento: registra la salida que lo revierte, referenciando la entrada")
    void cancelacionRevierteEntrada() {
        CatMotivo motivoVenta = CatMotivo.builder().idmotivo(1).nombre("Venta").tipoMov((byte) 1).catSat((byte) 1).activo(true).build();
        CatMotivo motivoCancel = CatMotivo.builder().idmotivo(2).nombre("Cancelación de venta").tipoMov((byte) 2).catSat((byte) 1).activo(true).build();
        when(catMotivoRepository.findByNombre("Venta")).thenReturn(Optional.of(motivoVenta));
        when(catMotivoRepository.findByNombre("Cancelación de venta")).thenReturn(Optional.of(motivoCancel));
        MovimientoCaja original = MovimientoCaja.builder().idmovimiento(30).caja(caja)
                .monto(new BigDecimal("199.00")).tipo((byte) 1).build();
        when(movimientoCajaRepository.findFirstByMotivo_IdmotivoAndObservaciones(1, "Venta #7"))
                .thenReturn(Optional.of(original));

        service.registrarCancelacionVenta(Venta.builder().idventa(7).caja(caja).build(), encargado);

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getTipo()).isEqualTo((byte) 2);
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("199.00");
        assertThat(cap.getValue().getIdmovRef()).isEqualTo(30);
    }

    // ── Abonos a cuentas por cobrar ──────────────────────────────────────────

    private CatMotivo motivoAbono() {
        CatMotivo abono = CatMotivo.builder().idmotivo(3).nombre("Abono de cuenta por cobrar")
                .tipoMov((byte) 1).catSat((byte) 1).activo(true).build();
        lenient().when(catMotivoRepository.findByNombre("Abono de cuenta por cobrar")).thenReturn(Optional.of(abono));
        return abono;
    }

    private CuentaPorCobrar cuenta() {
        return CuentaPorCobrar.builder().idcuenta(5).noFactura("V-000009").build();
    }

    private PagoCuenta pago() {
        return PagoCuenta.builder().idpago(77).monto(new BigDecimal("30.00")).build();
    }

    @Test
    @DisplayName("abono con caja indicada: entrada en esa caja, referenciando la cuenta y el pago")
    void abonoConCajaIndicada() {
        motivoAbono();

        service.registrarAbonoCuenta(ID_CAJA, cuenta(), pago(), encargado);

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getCaja()).isSameAs(caja);
        assertThat(cap.getValue().getTipo()).isEqualTo((byte) 1);
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("30.00");
        assertThat(cap.getValue().getObservaciones()).isEqualTo("Abono a cuenta V-000009 (pago #77)");
    }

    @Test
    @DisplayName("abono sin caja: usa la caja principal de la tienda del usuario")
    void abonoSinCaja_usaLaPrincipalDeLaTienda() {
        motivoAbono();
        caja.setEsCajaPrincipal(true);
        Caja secundaria = Caja.builder().idCaja(2).nombreCaja("Caja 2").tienda(tienda).esCajaPrincipal(false).build();
        when(cajaRepository.findByTienda_Codti(2)).thenReturn(List.of(secundaria, caja));

        service.registrarAbonoCuenta(null, cuenta(), pago(), encargado);

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getCaja()).isSameAs(caja);
    }

    @Test
    @DisplayName("abono sin caja y sin caja posible (usuario sin tienda con cajas): 400 pidiendo idCaja")
    void abonoSinCaja_sinCajaPosible() {
        motivoAbono();
        when(cajaRepository.findByTienda_Codti(2)).thenReturn(List.of());

        assertThatThrownBy(() -> service.registrarAbonoCuenta(null, cuenta(), pago(), encargado))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("idCaja");
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("abono en la caja de otra tienda por un encargado: 403")
    void abonoEnCajaDeOtraTienda() {
        motivoAbono();
        caja.setTienda(otraTienda);

        assertThatThrownBy(() -> service.registrarAbonoCuenta(ID_CAJA, cuenta(), pago(), encargado))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("entrada por el efectivo de un pago mixto: registra ese monto; con 0 no registra nada")
    void entradaVentaMixta() {
        CatMotivo motivoVenta = CatMotivo.builder().idmotivo(1).nombre("Venta").tipoMov((byte) 1).catSat((byte) 1).activo(true).build();
        when(catMotivoRepository.findByNombre("Venta")).thenReturn(Optional.of(motivoVenta));
        Venta venta = Venta.builder().idventa(9).caja(caja).total(new BigDecimal("998.00")).build();

        service.registrarEntradaVenta(venta, encargado, BigDecimal.ZERO);
        service.registrarEntradaVenta(venta, encargado, null);
        verify(movimientoCajaRepository, never()).save(any());

        service.registrarEntradaVenta(venta, encargado, new BigDecimal("500.00"));

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("500.00");
        assertThat(cap.getValue().getObservaciones()).isEqualTo("Venta #9");
    }

    // ── Anticipos de órdenes de servicio ─────────────────────────────────────

    private OrdenServicio orden() {
        return OrdenServicio.builder().idorden(1).folio("OS-000001").tienda(tienda).build();
    }

    private OrdenServicioAnticipo anticipoDeOrden() {
        return OrdenServicioAnticipo.builder().idanticipo(8).monto(new BigDecimal("500.00")).build();
    }

    private void motivos() {
        lenient().when(catMotivoRepository.findByNombre("Anticipo de servicio")).thenReturn(Optional.of(
                CatMotivo.builder().idmotivo(11).nombre("Anticipo de servicio").tipoMov((byte) 1).catSat((byte) 1).activo(true).build()));
        lenient().when(catMotivoRepository.findByNombre("Devolución de anticipo")).thenReturn(Optional.of(
                CatMotivo.builder().idmotivo(12).nombre("Devolución de anticipo").tipoMov((byte) 2).catSat((byte) 1).activo(true).build()));
        lenient().when(movimientoCajaRepository.save(any(MovimientoCaja.class))).thenAnswer(inv -> {
            MovimientoCaja m = inv.getArgument(0);
            m.setIdmovimiento(77);
            return m;
        });
    }

    @Test
    @DisplayName("anticipo en efectivo con caja indicada: entrada ligada a la orden y al anticipo; devuelve el id del movimiento")
    void anticipoConCajaIndicada() {
        motivos();

        Integer id = service.registrarAnticipoServicio(ID_CAJA, orden(), anticipoDeOrden(), encargado);

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(id).isEqualTo(77);
        assertThat(cap.getValue().getTipo()).isEqualTo((byte) 1);
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("500.00");
        assertThat(cap.getValue().getObservaciones()).isEqualTo("Anticipo orden OS-000001 (anticipo #8)");
    }

    @Test
    @DisplayName("anticipo sin caja: usa la caja principal de la tienda de la orden (no la del usuario)")
    void anticipoSinCaja() {
        motivos();
        caja.setEsCajaPrincipal(true);
        when(cajaRepository.findByTienda_Codti(2)).thenReturn(List.of(caja));

        service.registrarAnticipoServicio(null, orden(), anticipoDeOrden(), admin);

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getCaja()).isSameAs(caja);
    }

    @Test
    @DisplayName("una caja de otra tienda no sirve para el anticipo de la orden: 400")
    void anticipoCajaDeOtraTienda() {
        motivos();
        caja.setTienda(otraTienda);

        assertThatThrownBy(() -> service.registrarAnticipoServicio(ID_CAJA, orden(), anticipoDeOrden(), admin))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no pertenece");
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("devolver un anticipo: salida en la misma caja, referenciando la entrada original")
    void devolucionDeAnticipo() {
        motivos();
        MovimientoCaja original = MovimientoCaja.builder().idmovimiento(30).caja(caja).monto(new BigDecimal("500.00")).tipo((byte) 1).build();
        when(movimientoCajaRepository.findById(30)).thenReturn(Optional.of(original));
        OrdenServicioAnticipo anticipo = anticipoDeOrden();
        anticipo.setIdmovimientoCaja(30);

        service.registrarDevolucionAnticipo(orden(), anticipo, encargado);

        ArgumentCaptor<MovimientoCaja> cap = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(cap.capture());
        assertThat(cap.getValue().getTipo()).isEqualTo((byte) 2);
        assertThat(cap.getValue().getCaja()).isSameAs(caja);
        assertThat(cap.getValue().getIdmovRef()).isEqualTo(30);
        assertThat(cap.getValue().getMonto()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("devolver un anticipo cuyo movimiento ya no existe: 409")
    void devolucionSinMovimiento() {
        motivos();
        when(movimientoCajaRepository.findById(30)).thenReturn(Optional.empty());
        OrdenServicioAnticipo anticipo = anticipoDeOrden();
        anticipo.setIdmovimientoCaja(30);

        assertThatThrownBy(() -> service.registrarDevolucionAnticipo(orden(), anticipo, encargado))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("cancelar una venta sin movimiento (no fue efectivo / es anterior): no registra nada")
    void cancelacionSinMovimientoOriginal() {
        CatMotivo motivoVenta = CatMotivo.builder().idmotivo(1).nombre("Venta").tipoMov((byte) 1).catSat((byte) 1).activo(true).build();
        when(catMotivoRepository.findByNombre("Venta")).thenReturn(Optional.of(motivoVenta));
        when(movimientoCajaRepository.findFirstByMotivo_IdmotivoAndObservaciones(anyInt(), anyString()))
                .thenReturn(Optional.empty());

        service.registrarCancelacionVenta(Venta.builder().idventa(1).caja(caja).build(), encargado);

        verify(movimientoCajaRepository, never()).save(any());
    }
}
