package com.skycel.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.caja.AbrirCajaRequestDto;
import com.skycel.backend.dto.caja.CerrarCajaRequestDto;
import com.skycel.backend.dto.caja.SesionCajaResponseDto;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.skycel.backend.service.MotivoCajaService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Turnos de caja: abrir con fondo, cerrar con corte guardado, y que el saldo del sistema quede igual al cajón. */
@ExtendWith(MockitoExtension.class)
class SesionCajaServiceTest {

    @Mock private SesionCajaRepository     sesionCajaRepository;
    @Mock private CajaRepository           cajaRepository;
    @Mock private UsuarioRepository        usuarioRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;
    @Mock private MovimientoCajaService    movimientoCajaService;
    @Mock private VentaRepository          ventaRepository;

    private SesionCajaService service;

    private static final Integer ID_CAJA = 1;

    private Tienda zocalo, corpo;
    private Caja caja;
    private Usuario luis, root, vendedorDeOtraTienda;
    private final List<SesionCaja> sesiones = new ArrayList<>();
    private final List<MovimientoCaja> movimientos = new ArrayList<>();
    private BigDecimal entradas = BigDecimal.ZERO, salidas = BigDecimal.ZERO;
    private int idSesion = 1;

    @BeforeEach
    void setUp() {
        service = new SesionCajaService(sesionCajaRepository, cajaRepository, usuarioRepository, movimientoCajaRepository,
                movimientoCajaService, ventaRepository, new ObjectMapper());
        zocalo = Tienda.builder().codti(2).nombre("Zocalo").build();
        corpo = Tienda.builder().codti(3).nombre("Corpo").build();
        caja = Caja.builder().idCaja(ID_CAJA).nombreCaja("Caja Zocalo").tienda(zocalo).build();
        luis = Usuario.builder().idusuario(4).username("luis").nombreCompleto("Luis").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        root = Usuario.builder().idusuario(1).username("root").nombreCompleto("Root").rol(Rol.ROOT).tienda(zocalo).build();
        vendedorDeOtraTienda = Usuario.builder().idusuario(9).username("yami").nombreCompleto("Yami").rol(Rol.VENDEDOR).tienda(corpo).build();

        lenient().when(cajaRepository.findById(ID_CAJA)).thenReturn(Optional.of(caja));
        for (Usuario u : List.of(luis, root, vendedorDeOtraTienda)) lenient().when(usuarioRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));

        lenient().when(sesionCajaRepository.save(any(SesionCaja.class))).thenAnswer(inv -> {
            SesionCaja s = inv.getArgument(0);
            if (s.getIdsesionCaja() == null) { s.setIdsesionCaja(idSesion++); sesiones.add(s); }
            return s;
        });
        lenient().when(sesionCajaRepository.findFirstByCaja_IdCajaAndEstado(ID_CAJA, SesionCaja.ABIERTA)).thenAnswer(inv ->
                sesiones.stream().filter(s -> s.getEstado() == SesionCaja.ABIERTA).findFirst());
        lenient().when(sesionCajaRepository.findFirstByCaja_IdCajaAndEstadoOrderByFechaCierreDesc(ID_CAJA, SesionCaja.CERRADA)).thenAnswer(inv ->
                sesiones.stream().filter(s -> s.getEstado() == SesionCaja.CERRADA).reduce((a, b) -> b));
        lenient().when(sesionCajaRepository.findById(anyInt())).thenAnswer(inv ->
                sesiones.stream().filter(s -> s.getIdsesionCaja().equals(inv.getArgument(0))).findFirst());
        lenient().when(sesionCajaRepository.findByCaja_IdCajaOrderByFechaAperturaDesc(eq(ID_CAJA), any(Pageable.class))).thenAnswer(inv -> {
            List<SesionCaja> copia = new ArrayList<>(sesiones);
            java.util.Collections.reverse(copia);
            return copia;
        });
        lenient().when(movimientoCajaRepository.totalPorTipo(ID_CAJA, ENTRADA)).thenAnswer(inv -> entradas);
        lenient().when(movimientoCajaRepository.totalPorTipo(ID_CAJA, SALIDA)).thenAnswer(inv -> salidas);
        lenient().when(movimientoCajaRepository.findByCaja_IdCajaAndFechaMovBetweenOrderByFechaMovDesc(eq(ID_CAJA), any(), any()))
                .thenAnswer(inv -> new ArrayList<>(movimientos));
        lenient().when(ventaRepository.findByCaja_IdCajaAndFechaVentaBetween(eq(ID_CAJA), any(), any())).thenReturn(List.of());

        // Los movimientos del turno (fondo, ajuste, retiro) mueven el saldo, como en el servicio real
        lenient().when(movimientoCajaService.registrarDeTurno(any(Caja.class), any(Usuario.class), any(), any(BigDecimal.class), any())).thenAnswer(inv -> {
            String motivo = inv.getArgument(2);
            BigDecimal monto = inv.getArgument(3);
            if (motivo.equals(FONDO_APERTURA)) entradas = entradas.add(monto); else salidas = salidas.add(monto);
            return movimiento(motivo, motivo.equals(FONDO_APERTURA) ? ENTRADA : SALIDA, monto);
        });
    }

    private MovimientoCaja movimiento(String motivo, byte tipo, BigDecimal monto) {
        return MovimientoCaja.builder().caja(caja).usuarioEncargado(luis)
                .motivo(CatMotivo.builder().nombre(motivo).tipoMov(tipo).build()).tipo(tipo).monto(monto).fechaMov(LocalDateTime.now()).build();
    }

    private void venderEnEfectivo(String monto) {
        BigDecimal m = new BigDecimal(monto);
        entradas = entradas.add(m);
        movimientos.add(movimiento(VENTA, ENTRADA, m));
    }

    private void gastar(String monto) {
        BigDecimal m = new BigDecimal(monto);
        salidas = salidas.add(m);
        movimientos.add(movimiento("Gasto operativo", SALIDA, m));
    }

    private AbrirCajaRequestDto abrirCon(String fondo) {
        AbrirCajaRequestDto d = new AbrirCajaRequestDto();
        d.setFondoInicial(new BigDecimal(fondo));
        return d;
    }

    private CerrarCajaRequestDto cerrarCon(String contado, String fondoSiguiente, String obs) {
        CerrarCajaRequestDto d = new CerrarCajaRequestDto();
        d.setEfectivoContado(new BigDecimal(contado));
        d.setFondoSiguiente(fondoSiguiente != null ? new BigDecimal(fondoSiguiente) : null);
        d.setObservaciones(obs);
        return d;
    }

    @Test
    @DisplayName("abrir por primera vez: el fondo entra como 'Fondo de apertura' y el turno queda abierto")
    void abrirPrimeraVez() {
        SesionCajaResponseDto r = service.abrir(ID_CAJA, abrirCon("1500"), "luis");

        assertThat(r.isAbierta()).isTrue();
        assertThat(r.getFondoInicial()).isEqualByComparingTo("1500");
        assertThat(r.getEfectivoEsperado()).isEqualByComparingTo("1500");
        assertThat(r.getUsuarioApertura()).isEqualTo("Luis");
        verify(movimientoCajaService).registrarDeTurno(eq(caja), eq(luis), eq(FONDO_APERTURA), argThat(m -> m.compareTo(new BigDecimal("1500.00")) == 0), any());
    }

    @Test
    @DisplayName("abrir sin conexión: el turno conserva la fecha real y la clave, y el fondo se registra con esa fecha")
    void abrirSinConexion() {
        lenient().when(movimientoCajaService.registrarDeTurno(any(Caja.class), any(Usuario.class), any(), any(BigDecimal.class), any(), any(LocalDateTime.class)))
                .thenAnswer(inv -> movimiento(inv.getArgument(2), ENTRADA, inv.getArgument(3)));
        LocalDateTime ayer = LocalDateTime.now().minusHours(5).withNano(0);
        AbrirCajaRequestDto dto = abrirCon("1000");
        dto.setFecha(ayer);
        dto.setClaveOffline("clave-abrir-1");

        SesionCajaResponseDto r = service.abrir(ID_CAJA, dto, "luis");

        assertThat(sesiones.get(0).getFechaApertura()).isEqualTo(ayer);
        assertThat(sesiones.get(0).getClaveApertura()).isEqualTo("clave-abrir-1");
        assertThat(r.isAbierta()).isTrue();
        verify(movimientoCajaService).registrarDeTurno(eq(caja), eq(luis), eq(FONDO_APERTURA), any(BigDecimal.class), any(), eq(ayer));
    }

    @Test
    @DisplayName("abrir sin conexión: reenviar la misma clave devuelve el mismo turno y no abre otro")
    void abrirSinConexionReenviado() {
        AbrirCajaRequestDto dto = abrirCon("1000");
        dto.setFecha(LocalDateTime.now().minusHours(2));
        dto.setClaveOffline("clave-abrir-2");
        service.abrir(ID_CAJA, dto, "luis");
        when(sesionCajaRepository.findFirstByClaveApertura("clave-abrir-2")).thenAnswer(inv -> sesiones.stream().findFirst());

        SesionCajaResponseDto otra = service.abrir(ID_CAJA, dto, "luis");

        assertThat(sesiones).hasSize(1);
        assertThat(otra.getIdsesionCaja()).isEqualTo(sesiones.get(0).getIdsesionCaja());
    }

    @Test
    @DisplayName("abrir sin conexión con una fecha del futuro: se usa la hora de ahora")
    void abrirSinConexionFechaFutura() {
        lenient().when(movimientoCajaService.registrarDeTurno(any(Caja.class), any(Usuario.class), any(), any(BigDecimal.class), any(), any(LocalDateTime.class)))
                .thenAnswer(inv -> movimiento(inv.getArgument(2), ENTRADA, inv.getArgument(3)));
        AbrirCajaRequestDto dto = abrirCon("1000");
        dto.setFecha(LocalDateTime.now().plusDays(3));
        dto.setClaveOffline("clave-abrir-3");

        service.abrir(ID_CAJA, dto, "luis");

        assertThat(sesiones.get(0).getFechaApertura()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("cerrar sin conexión: el corte queda con la fecha real y reenviar la misma clave devuelve el mismo corte")
    void cerrarSinConexion() {
        lenient().when(movimientoCajaService.registrarDeTurno(any(Caja.class), any(Usuario.class), any(), any(BigDecimal.class), any(), any(LocalDateTime.class)))
                .thenAnswer(inv -> movimiento(inv.getArgument(2), SALIDA, inv.getArgument(3)));
        service.abrir(ID_CAJA, abrirCon("1500"), "luis");
        LocalDateTime hace1h = LocalDateTime.now().minusHours(1).withNano(0);
        CerrarCajaRequestDto dto = cerrarCon("1500", "1500", null);
        dto.setFecha(hace1h);
        dto.setClaveOffline("clave-cerrar-1");

        SesionCajaResponseDto r = service.cerrar(ID_CAJA, dto, "luis");

        assertThat(sesiones.get(0).getFechaCierre()).isEqualTo(hace1h);
        assertThat(sesiones.get(0).getClaveCierre()).isEqualTo("clave-cerrar-1");
        when(sesionCajaRepository.findFirstByClaveCierre("clave-cerrar-1")).thenAnswer(inv -> sesiones.stream().findFirst());
        SesionCajaResponseDto otra = service.cerrar(ID_CAJA, dto, "luis");
        assertThat(otra.getIdsesionCaja()).isEqualTo(r.getIdsesionCaja());
    }

    @Test
    @DisplayName("no se puede abrir una caja que ya está abierta")
    void abrirDosVeces() {
        service.abrir(ID_CAJA, abrirCon("1000"), "luis");
        assertThatThrownBy(() -> service.abrir(ID_CAJA, abrirCon("1000"), "luis"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("ya está abierta");
                });
        assertThat(sesiones).hasSize(1);
    }

    @Test
    @DisplayName("abrir con un fondo menor al saldo que dejó el sistema registra un 'Ajuste de apertura' (salida)")
    void abrirConMenosDeLoEsperado() {
        entradas = new BigDecimal("1000");                       // el sistema cree que hay $1,000
        service.abrir(ID_CAJA, abrirCon("900"), "luis");

        verify(movimientoCajaService).registrarDeTurno(eq(caja), eq(luis), eq(AJUSTE_APERTURA), argThat(m -> m.compareTo(new BigDecimal("100.00")) == 0), any());
        assertThat(entradas.subtract(salidas)).isEqualByComparingTo("900");
    }

    @Test
    @DisplayName("abrir con el mismo fondo que ya hay no registra ningún movimiento")
    void abrirConElSaldoExacto() {
        entradas = new BigDecimal("1500");
        service.abrir(ID_CAJA, abrirCon("1500"), "luis");
        verify(movimientoCajaService, never()).registrarDeTurno(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cerrar: guarda esperado, contado, diferencia y el corte; retira lo que no se deja para mañana")
    void cerrarGuardaElCorte() {
        service.abrir(ID_CAJA, abrirCon("1500"), "luis");
        venderEnEfectivo("1251.00");
        gastar("40.00");                                          // esperado = 1500 + 1251 - 40 = 2711

        SesionCajaResponseDto r = service.cerrar(ID_CAJA, cerrarCon("2700", "1500", "Faltaron $11"), "luis");

        assertThat(r.isAbierta()).isFalse();
        assertThat(r.getEfectivoEsperado()).isEqualByComparingTo("2711");
        assertThat(r.getEfectivoContado()).isEqualByComparingTo("2700");
        assertThat(r.getDiferencia()).isEqualByComparingTo("-11");
        assertThat(r.getFondoSiguiente()).isEqualByComparingTo("1500");
        assertThat(r.getRetiroCierre()).isEqualByComparingTo("1200");
        assertThat(r.getObservaciones()).isEqualTo("Faltaron $11");
        assertThat(r.getUsuarioCierre()).isEqualTo("Luis");
        verify(movimientoCajaService).registrarDeTurno(eq(caja), eq(luis), eq(RETIRO_CIERRE), argThat(m -> m.compareTo(new BigDecimal("1200.00")) == 0), any());

        // El corte quedó guardado con el detalle por motivo (sin contar el fondo ni el retiro)
        SesionCajaResponseDto guardado = service.obtener(r.getIdsesionCaja(), "luis");
        assertThat(guardado.getResumen().getTotalEntradas()).isEqualByComparingTo("1251");
        assertThat(guardado.getResumen().getTotalSalidas()).isEqualByComparingTo("40");
        assertThat(guardado.getResumen().getMovimientos()).extracting("motivo").containsExactly(VENTA, "Gasto operativo");
    }

    @Test
    @DisplayName("el detalle del corte agrupa las ventas por método de pago y no cuenta las canceladas")
    void ventasPorMetodo() {
        service.abrir(ID_CAJA, abrirCon("1000"), "luis");
        when(ventaRepository.findByCaja_IdCajaAndFechaVentaBetween(eq(ID_CAJA), any(), any())).thenReturn(List.of(
                Venta.builder().estado((byte) 1).metodoPago((byte) 1).total(new BigDecimal("500")).build(),
                Venta.builder().estado((byte) 1).metodoPago((byte) 1).total(new BigDecimal("250")).build(),
                Venta.builder().estado((byte) 1).metodoPago((byte) 2).total(new BigDecimal("4081")).build(),
                Venta.builder().estado((byte) 2).metodoPago((byte) 1).total(new BigDecimal("99")).build()));

        SesionCajaResponseDto r = service.cerrar(ID_CAJA, cerrarCon("1000", null, null), "luis");
        SesionCajaResponseDto.ResumenDto res = service.obtener(r.getIdsesionCaja(), "luis").getResumen();

        assertThat(res.getNumVentas()).isEqualTo(3);
        assertThat(res.getTotalVentas()).isEqualByComparingTo("4831");
        assertThat(res.getVentasCanceladas()).isEqualTo(1);
        assertThat(res.getVentasPorMetodo()).extracting("metodo").containsExactly("Efectivo", "Tarjeta");
        assertThat(res.getVentasPorMetodo().get(0).getCantidad()).isEqualTo(2);
        assertThat(res.getVentasPorMetodo().get(0).getTotal()).isEqualByComparingTo("750");
    }

    @Test
    @DisplayName("sin indicar el fondo para mañana se deja el fondo con el que se abrió; si no alcanza, lo contado")
    void fondoSiguientePorOmision() {
        service.abrir(ID_CAJA, abrirCon("1500"), "luis");
        venderEnEfectivo("300");
        assertThat(service.cerrar(ID_CAJA, cerrarCon("1800", null, null), "luis").getFondoSiguiente()).isEqualByComparingTo("1500");
        assertThat(sesiones.get(0).getRetiroCierre()).isEqualByComparingTo("300");

        // otro día: se abre con 1500 y solo se cuentan 800
        service.abrir(ID_CAJA, abrirCon("1500"), "luis");
        assertThat(service.cerrar(ID_CAJA, cerrarCon("800", null, null), "luis").getFondoSiguiente()).isEqualByComparingTo("800");
    }

    @Test
    @DisplayName("el fondo para mañana no puede ser mayor a lo contado, y sin retiro no se registra movimiento")
    void fondoMayorAlContado() {
        service.abrir(ID_CAJA, abrirCon("500"), "luis");
        assertThatThrownBy(() -> service.cerrar(ID_CAJA, cerrarCon("400", "500", null), "luis"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(sesiones.get(0).getEstado()).isEqualTo(SesionCaja.ABIERTA);

        service.cerrar(ID_CAJA, cerrarCon("500", "500", null), "luis");
        verify(movimientoCajaService, never()).registrarDeTurno(any(), any(), eq(RETIRO_CIERRE), any(), any());
    }

    @Test
    @DisplayName("no se puede cerrar una caja que no está abierta")
    void cerrarSinAbrir() {
        assertThatThrownBy(() -> service.cerrar(ID_CAJA, cerrarCon("100", null, null), "luis"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("con la caja cerrada, 'actual' sugiere abrir con lo que quedó en el cajón")
    void fondoSugerido() {
        service.abrir(ID_CAJA, abrirCon("1500"), "luis");
        venderEnEfectivo("200");
        service.cerrar(ID_CAJA, cerrarCon("1650", "1500", null), "luis");      // contó $50 menos de lo esperado; deja 1,500

        SesionCajaResponseDto r = service.actual(ID_CAJA, "luis");
        assertThat(r.isAbierta()).isFalse();
        assertThat(r.getFondoSugerido()).isEqualByComparingTo("1500");   // lo que se dejó, no el saldo del sistema (que cree que hay $50 más)
    }

    @Test
    @DisplayName("el historial trae los turnos con el más reciente primero, y los abiertos con su resumen en vivo")
    void historial() {
        service.abrir(ID_CAJA, abrirCon("1000"), "luis");
        service.cerrar(ID_CAJA, cerrarCon("1000", "1000", null), "luis");
        service.abrir(ID_CAJA, abrirCon("1000"), "luis");

        List<SesionCajaResponseDto> h = service.historial(ID_CAJA, 60, "luis");
        assertThat(h).hasSize(2);
        assertThat(h.get(0).isAbierta()).isTrue();
        assertThat(h.get(1).isAbierta()).isFalse();
    }

    @Test
    @DisplayName("un encargado no maneja la caja de otra tienda; un vendedor sí (rotan de sucursal) y un administrador también")
    void acceso() {
        Usuario encargadoCorpo = Usuario.builder().idusuario(5).username("memo").rol(Rol.ENCARGADO_TIENDA).tienda(corpo).build();
        lenient().when(usuarioRepository.findByUsername("memo")).thenReturn(Optional.of(encargadoCorpo));

        assertThatThrownBy(() -> service.abrir(ID_CAJA, abrirCon("100"), "memo"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service.abrir(ID_CAJA, abrirCon("100"), "yami").isAbierta()).isTrue();
        service.cerrar(ID_CAJA, cerrarCon("100", "100", null), "root");
        assertThat(sesiones.get(0).getUsuarioCierre().getUsername()).isEqualTo("root");
    }

    @Test
    @DisplayName("si después de cerrar entra algo con fecha del turno, el corte avisa que cambió")
    void modificadaDespuesDelCierre() {
        service.abrir(ID_CAJA, abrirCon("1000"), "luis");
        SesionCajaResponseDto r = service.cerrar(ID_CAJA, cerrarCon("1000", "1000", null), "luis");
        assertThat(service.obtener(r.getIdsesionCaja(), "luis").isModificadaDespuesDelCierre()).isFalse();

        venderEnEfectivo("120");                                  // una venta hecha sin conexión que llega tarde
        assertThat(service.obtener(r.getIdsesionCaja(), "luis").isModificadaDespuesDelCierre()).isTrue();
    }
}
