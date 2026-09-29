package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.EmpleadoPrestamo;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.EmpleadoPrestamoRepository;
import com.skycel.backend.repository.UsuarioRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de préstamos/adelantos a empleados (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class EmpleadoPrestamoServiceTest {

    @Mock private EmpleadoPrestamoRepository prestamoRepository;
    @Mock private EmpleadoPerfilRepository empleadoPerfilRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MovimientoCajaService movimientoCajaService;

    @InjectMocks
    private EmpleadoPrestamoService service;

    @Captor private ArgumentCaptor<EmpleadoPrestamo> prestamoCaptor;

    private Usuario admin;
    private EmpleadoPerfil empleado;

    @BeforeEach
    void setUp() {
        admin = Usuario.builder().idusuario(1).username("admin").rol(Rol.ADMIN).build();
        Usuario uEmpleado = Usuario.builder().idusuario(2).nombreCompleto("Juan").tienda(Tienda.builder().codti(2).build()).build();
        empleado = EmpleadoPerfil.builder().idempleado(5).usuario(uEmpleado).sueldoBase(new BigDecimal("1000")).build();

        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(empleadoPerfilRepository.findById(5)).thenReturn(Optional.of(empleado));
        lenient().when(prestamoRepository.save(any(EmpleadoPrestamo.class))).thenAnswer(inv -> {
            EmpleadoPrestamo p = inv.getArgument(0);
            if (p.getIdprestamo() == null) p.setIdprestamo(99);
            return p;
        });
    }

    @Test
    @DisplayName("otorgar: crea el préstamo y registra la salida de caja")
    void otorgar_creaYRegistraSalida() {
        when(prestamoRepository.findByEmpleado_IdempleadoAndEstado(5, EmpleadoPrestamoService.ACTIVO)).thenReturn(Optional.empty());

        Map<String, Object> r = service.otorgar(5, new BigDecimal("500"), 1, "adelanto de sueldo", "admin");

        assertThat(r.get("saldoPendiente")).isEqualTo(new BigDecimal("500"));
        verify(movimientoCajaService).registrarPrestamoEmpleado(eq(1), prestamoCaptor.capture(), eq(admin));
        assertThat(prestamoCaptor.getValue().getMontoOriginal()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("otorgar: monto <= 0 -> 400")
    void otorgar_montoInvalido_lanza400() {
        assertThatThrownBy(() -> service.otorgar(5, BigDecimal.ZERO, 1, null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(movimientoCajaService);
    }

    @Test
    @DisplayName("otorgar: si ya tiene un préstamo activo -> 409")
    void otorgar_yaTienePrestamoActivo_lanza409() {
        when(prestamoRepository.findByEmpleado_IdempleadoAndEstado(5, EmpleadoPrestamoService.ACTIVO))
                .thenReturn(Optional.of(EmpleadoPrestamo.builder().idprestamo(1).build()));

        assertThatThrownBy(() -> service.otorgar(5, new BigDecimal("500"), 1, null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(movimientoCajaService);
    }

    @Test
    @DisplayName("abonar: reduce el saldo y no liquida si queda un remanente")
    void abonar_reduceSaldo() {
        EmpleadoPrestamo p = EmpleadoPrestamo.builder().idprestamo(1).empleado(empleado)
                .montoOriginal(new BigDecimal("1000")).saldoPendiente(new BigDecimal("1000")).estado(EmpleadoPrestamoService.ACTIVO).build();
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(p));

        service.abonar(1, new BigDecimal("300"));

        assertThat(p.getSaldoPendiente()).isEqualByComparingTo("700");
        assertThat(p.getEstado()).isEqualTo(EmpleadoPrestamoService.ACTIVO);
    }

    @Test
    @DisplayName("abonar: si el abono salda el préstamo, queda LIQUIDADO")
    void abonar_saldaCompleto_liquida() {
        EmpleadoPrestamo p = EmpleadoPrestamo.builder().idprestamo(1).empleado(empleado)
                .montoOriginal(new BigDecimal("1000")).saldoPendiente(new BigDecimal("300")).estado(EmpleadoPrestamoService.ACTIVO).build();
        when(prestamoRepository.findById(1)).thenReturn(Optional.of(p));

        service.abonar(1, new BigDecimal("300"));

        assertThat(p.getSaldoPendiente()).isEqualByComparingTo("0");
        assertThat(p.getEstado()).isEqualTo(EmpleadoPrestamoService.LIQUIDADO);
    }
}
