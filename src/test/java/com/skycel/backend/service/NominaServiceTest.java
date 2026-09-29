package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas unitarias del servicio de nómina (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class NominaServiceTest {

    @Mock private NominaPeriodoRepository periodoRepository;
    @Mock private NominaDetalleRepository detalleRepository;
    @Mock private NominaPercepcionRepository percepcionRepository;
    @Mock private NominaDeduccionRepository deduccionRepository;
    @Mock private EmpleadoPerfilRepository empleadoPerfilRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private CajaRepository cajaRepository;
    @Mock private EmpleadoPrestamoService prestamoService;
    @Mock private MovimientoCajaService movimientoCajaService;

    @InjectMocks
    private NominaService service;

    @Captor private ArgumentCaptor<NominaPeriodo> periodoCaptor;
    @Captor private ArgumentCaptor<NominaDetalle> detalleCaptor;
    @Captor private ArgumentCaptor<NominaPercepcion> percepcionCaptor;
    @Captor private ArgumentCaptor<NominaDeduccion> deduccionCaptor;

    private Tienda zocalo;
    private Usuario admin;
    private int idSecuencia;

    @BeforeEach
    void setUp() {
        zocalo = Tienda.builder().codti(2).nombre("Zócalo").build();
        admin = Usuario.builder().idusuario(1).username("admin").nombreCompleto("Admin").rol(Rol.ADMIN).build();
        idSecuencia = 1000;

        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(periodoRepository.save(any(NominaPeriodo.class))).thenAnswer(inv -> {
            NominaPeriodo p = inv.getArgument(0);
            if (p.getIdperiodo() == null) p.setIdperiodo(idSecuencia++);
            return p;
        });
        lenient().when(detalleRepository.save(any(NominaDetalle.class))).thenAnswer(inv -> {
            NominaDetalle d = inv.getArgument(0);
            if (d.getIddetalle() == null) d.setIddetalle(idSecuencia++);
            return d;
        });
        lenient().when(percepcionRepository.save(any(NominaPercepcion.class))).thenAnswer(inv -> {
            NominaPercepcion p = inv.getArgument(0);
            if (p.getIdpercepcion() == null) p.setIdpercepcion(idSecuencia++);
            return p;
        });
        lenient().when(deduccionRepository.save(any(NominaDeduccion.class))).thenAnswer(inv -> {
            NominaDeduccion d = inv.getArgument(0);
            if (d.getIddeduccion() == null) d.setIddeduccion(idSecuencia++);
            return d;
        });
        lenient().when(percepcionRepository.findByDetalle_IddetalleOrderByIdpercepcionAsc(anyInt())).thenReturn(List.of());
        lenient().when(deduccionRepository.findByDetalle_IddetalleOrderByIddeduccionAsc(anyInt())).thenReturn(List.of());
        lenient().when(prestamoService.activoDe(any())).thenReturn(Optional.empty());
    }

    private EmpleadoPerfil empleado(int idempleado, String nombre, BigDecimal sueldo) {
        Usuario u = Usuario.builder().idusuario(idempleado + 100).nombreCompleto(nombre).tienda(zocalo).rol(Rol.VENDEDOR).build();
        return EmpleadoPerfil.builder().idempleado(idempleado).usuario(u).sueldoBase(sueldo).activo(true).build();
    }

    // ── Abrir período ────────────────────────────────────────────────────────

    @Test
    @DisplayName("abrirPeriodo: crea una línea por empleado activo con su sueldo base como percepción")
    void abrirPeriodo_creaLineasConSueldoBase() {
        when(periodoRepository.findFirstByEstadoOrderByFechaInicioDesc(NominaService.PERIODO_ABIERTO)).thenReturn(Optional.empty());
        when(empleadoPerfilRepository.findByActivoTrue()).thenReturn(List.of(
                empleado(1, "Juan", new BigDecimal("3000")),
                empleado(2, "Ana", new BigDecimal("2500"))));

        Map<String, Object> r = service.abrirPeriodo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16), "admin");

        assertThat(r.get("estado")).isEqualTo(NominaService.PERIODO_ABIERTO);
        // 2 veces por empleado: al crear la línea y al recalcular sus totales tras agregar el sueldo base.
        verify(detalleRepository, times(4)).save(any(NominaDetalle.class));
        verify(percepcionRepository, times(2)).save(percepcionCaptor.capture());
        assertThat(percepcionCaptor.getAllValues()).extracting(NominaPercepcion::getConcepto).containsOnly("Sueldo base");
        assertThat(percepcionCaptor.getAllValues()).extracting(NominaPercepcion::getMonto)
                .containsExactlyInAnyOrder(new BigDecimal("3000"), new BigDecimal("2500"));
    }

    @Test
    @DisplayName("abrirPeriodo: si ya hay uno abierto -> 409")
    void abrirPeriodo_yaHayAbierto_lanza409() {
        NominaPeriodo abierto = NominaPeriodo.builder().idperiodo(1).estado(NominaService.PERIODO_ABIERTO).build();
        when(periodoRepository.findFirstByEstadoOrderByFechaInicioDesc(NominaService.PERIODO_ABIERTO)).thenReturn(Optional.of(abierto));

        assertThatThrownBy(() -> service.abrirPeriodo(LocalDate.now(), LocalDate.now().plusDays(14), LocalDate.now().plusDays(15), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(empleadoPerfilRepository);
    }

    @Test
    @DisplayName("abrirPeriodo: fecha fin anterior a inicio -> 400")
    void abrirPeriodo_fechasInvalidas_lanza400() {
        assertThatThrownBy(() -> service.abrirPeriodo(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("abrirPeriodo: si el empleado tiene un préstamo activo, sugiere el abono como deducción, topado al sueldo")
    void abrirPeriodo_conPrestamoActivo_sugiereAbono() {
        when(periodoRepository.findFirstByEstadoOrderByFechaInicioDesc(NominaService.PERIODO_ABIERTO)).thenReturn(Optional.empty());
        EmpleadoPerfil emp = empleado(1, "Juan", new BigDecimal("1000"));
        when(empleadoPerfilRepository.findByActivoTrue()).thenReturn(List.of(emp));
        EmpleadoPrestamo prestamo = EmpleadoPrestamo.builder().idprestamo(50).empleado(emp)
                .montoOriginal(new BigDecimal("1500")).saldoPendiente(new BigDecimal("1500"))
                .fecha(LocalDate.now()).estado(EmpleadoPrestamoService.ACTIVO).build();
        when(prestamoService.activoDe(1)).thenReturn(Optional.of(prestamo));

        service.abrirPeriodo(LocalDate.now(), LocalDate.now().plusDays(14), LocalDate.now().plusDays(15), "admin");

        verify(deduccionRepository).save(deduccionCaptor.capture());
        NominaDeduccion d = deduccionCaptor.getValue();
        assertThat(d.getConcepto()).isEqualTo("Abono a préstamo");
        assertThat(d.getMonto()).isEqualByComparingTo("1000"); // topado al sueldo de 1000, aunque el saldo sea 1500
        assertThat(d.getPrestamo()).isEqualTo(prestamo);
    }

    // ── Percepciones / deducciones ───────────────────────────────────────────

    private NominaDetalle detallePendiente(int id) {
        EmpleadoPerfil emp = empleado(1, "Juan", new BigDecimal("1000"));
        return NominaDetalle.builder().iddetalle(id).empleado(emp)
                .periodo(NominaPeriodo.builder().idperiodo(1).estado(NominaService.PERIODO_ABIERTO).build())
                .estado(NominaService.DETALLE_PENDIENTE)
                .totalPercepciones(BigDecimal.ZERO).totalDeducciones(BigDecimal.ZERO).totalNeto(BigDecimal.ZERO).build();
    }

    @Test
    @DisplayName("agregarPercepcion: agrega la línea y recalcula el total")
    void agregarPercepcion_agregaYRecalcula() {
        NominaDetalle detalle = detallePendiente(10);
        when(detalleRepository.findById(10)).thenReturn(Optional.of(detalle));
        when(percepcionRepository.findByDetalle_IddetalleOrderByIdpercepcionAsc(10))
                .thenReturn(List.of(NominaPercepcion.builder().idpercepcion(1).concepto("Bono").monto(new BigDecimal("200")).build()));

        service.agregarPercepcion(10, "Bono", new BigDecimal("200"));

        verify(percepcionRepository).save(percepcionCaptor.capture());
        assertThat(percepcionCaptor.getValue().getConcepto()).isEqualTo("Bono");
        assertThat(detalle.getTotalPercepciones()).isEqualByComparingTo("200");
        assertThat(detalle.getTotalNeto()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("agregarPercepcion: sobre una línea ya pagada -> 400")
    void agregarPercepcion_lineaPagada_lanza400() {
        NominaDetalle detalle = detallePendiente(10);
        detalle.setEstado(NominaService.DETALLE_PAGADO);
        when(detalleRepository.findById(10)).thenReturn(Optional.of(detalle));

        assertThatThrownBy(() -> service.agregarPercepcion(10, "Bono", new BigDecimal("200")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(percepcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("agregarDeduccion: monto <= 0 -> 400")
    void agregarDeduccion_montoInvalido_lanza400() {
        NominaDetalle detalle = detallePendiente(10);
        when(detalleRepository.findById(10)).thenReturn(Optional.of(detalle));

        assertThatThrownBy(() -> service.agregarDeduccion(10, "Falta", BigDecimal.ZERO))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── Pagar ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pagar: registra el movimiento de caja, abona préstamos ligados y marca la línea pagada")
    void pagar_registraMovimientoYAbonaPrestamo() {
        NominaDetalle detalle = detallePendiente(10);
        detalle.setTotalPercepciones(new BigDecimal("1000"));
        detalle.setTotalDeducciones(new BigDecimal("300"));
        detalle.setTotalNeto(new BigDecimal("700"));
        when(detalleRepository.findById(10)).thenReturn(Optional.of(detalle));
        NominaDeduccion abono = NominaDeduccion.builder().iddeduccion(5).monto(new BigDecimal("300"))
                .prestamo(EmpleadoPrestamo.builder().idprestamo(50).build()).build();
        when(deduccionRepository.findByDetalle_IddetalleOrderByIddeduccionAsc(10)).thenReturn(List.of(abono));
        Caja caja = Caja.builder().idCaja(1).nombreCaja("Bodega").tienda(Tienda.builder().codti(1).build()).build();
        when(cajaRepository.findById(1)).thenReturn(Optional.of(caja));

        service.pagar(10, 1, "admin");

        verify(movimientoCajaService).registrarPagoNomina(eq(1), eq(detalle), eq(admin));
        verify(prestamoService).abonar(50, new BigDecimal("300"));
        assertThat(detalle.getEstado()).isEqualTo(NominaService.DETALLE_PAGADO);
        assertThat(detalle.getCaja()).isEqualTo(caja);
    }

    @Test
    @DisplayName("pagar: neto negativo -> 400, no se registra nada")
    void pagar_netoNegativo_lanza400() {
        NominaDetalle detalle = detallePendiente(10);
        detalle.setTotalNeto(new BigDecimal("-50"));
        when(detalleRepository.findById(10)).thenReturn(Optional.of(detalle));

        assertThatThrownBy(() -> service.pagar(10, 1, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(movimientoCajaService);
    }

    // ── Cerrar período ───────────────────────────────────────────────────────

    @Test
    @DisplayName("cerrarPeriodo: con líneas pendientes -> 400")
    void cerrarPeriodo_conPendientes_lanza400() {
        NominaPeriodo periodo = NominaPeriodo.builder().idperiodo(1).estado(NominaService.PERIODO_ABIERTO).build();
        when(periodoRepository.findById(1)).thenReturn(Optional.of(periodo));
        when(detalleRepository.countByPeriodo_IdperiodoAndEstado(1, NominaService.DETALLE_PENDIENTE)).thenReturn(2L);

        assertThatThrownBy(() -> service.cerrarPeriodo(1))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("cerrarPeriodo: sin pendientes, cierra")
    void cerrarPeriodo_sinPendientes_cierra() {
        NominaPeriodo periodo = NominaPeriodo.builder().idperiodo(1).estado(NominaService.PERIODO_ABIERTO).build();
        when(periodoRepository.findById(1)).thenReturn(Optional.of(periodo));
        when(detalleRepository.countByPeriodo_IdperiodoAndEstado(1, NominaService.DETALLE_PENDIENTE)).thenReturn(0L);
        when(detalleRepository.findByPeriodo_IdperiodoOrderByIddetalleAsc(1)).thenReturn(List.of());

        Map<String, Object> r = service.cerrarPeriodo(1);

        assertThat(r.get("estado")).isEqualTo(NominaService.PERIODO_CERRADO);
    }
}
