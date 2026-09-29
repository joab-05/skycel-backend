package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EmpleadoDescanso;
import com.skycel.backend.domain.entity.EmpleadoDescansoAjuste;
import com.skycel.backend.domain.entity.EmpleadoDescansoSaldo;
import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.EmpleadoDescansoAjusteRepository;
import com.skycel.backend.repository.EmpleadoDescansoRepository;
import com.skycel.backend.repository.EmpleadoDescansoSaldoRepository;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de descansos: bitácora de fechas y el saldo semi-automático por categoría (sin Spring ni BD). */
@ExtendWith(MockitoExtension.class)
class EmpleadoDescansoServiceTest {

    @Mock private EmpleadoDescansoRepository descansoRepository;
    @Mock private EmpleadoDescansoSaldoRepository saldoRepository;
    @Mock private EmpleadoDescansoAjusteRepository ajusteRepository;
    @Mock private EmpleadoPerfilRepository empleadoPerfilRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks
    private EmpleadoDescansoService service;

    private Tienda zocalo, superche, mina, otra;
    private Usuario admin, encargadoZocalo, encargadoOtra, encargadoMina;
    private EmpleadoPerfil empleadoZocalo;

    @BeforeEach
    void setUp() {
        zocalo = Tienda.builder().codti(2).nombre("Zócalo").build();
        superche = Tienda.builder().codti(4).nombre("Superche").build();
        mina = Tienda.builder().codti(3).nombre("Mina").build();
        otra = Tienda.builder().codti(9).nombre("Otra").build();
        admin = Usuario.builder().idusuario(1).username("admin").rol(Rol.ADMIN).build();
        encargadoZocalo = Usuario.builder().idusuario(2).username("enc2").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        encargadoOtra = Usuario.builder().idusuario(3).username("enc9").rol(Rol.ENCARGADO_TIENDA).tienda(otra).build();
        encargadoMina = Usuario.builder().idusuario(6).username("enc3").rol(Rol.ENCARGADO_TIENDA).tienda(mina).build();
        Usuario uEmpleado = Usuario.builder().idusuario(4).nombreCompleto("Juan").tienda(zocalo).rol(Rol.VENDEDOR).build();
        empleadoZocalo = EmpleadoPerfil.builder().idempleado(5).usuario(uEmpleado).build();

        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("enc2")).thenReturn(Optional.of(encargadoZocalo));
        lenient().when(usuarioRepository.findByUsername("enc9")).thenReturn(Optional.of(encargadoOtra));
        lenient().when(usuarioRepository.findByUsername("enc3")).thenReturn(Optional.of(encargadoMina));
        lenient().when(empleadoPerfilRepository.findById(5)).thenReturn(Optional.of(empleadoZocalo));
        lenient().when(descansoRepository.save(any(EmpleadoDescanso.class))).thenAnswer(inv -> {
            EmpleadoDescanso d = inv.getArgument(0);
            if (d.getIddescanso() == null) d.setIddescanso(1);
            return d;
        });
        lenient().when(saldoRepository.save(any(EmpleadoDescansoSaldo.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ajusteRepository.save(any(EmpleadoDescansoAjuste.class))).thenAnswer(inv -> {
            EmpleadoDescansoAjuste a = inv.getArgument(0);
            if (a.getIdajuste() == null) a.setIdajuste(1);
            return a;
        });
        lenient().when(ajusteRepository.findByEmpleado_Idempleado(anyInt())).thenReturn(List.of());
        lenient().when(descansoRepository.countByEmpleado_IdempleadoAndFechaGreaterThanEqual(anyInt(), any())).thenReturn(0L);
        lenient().when(saldoRepository.findById(5)).thenReturn(Optional.empty());
    }

    // ── Bitácora (ya existía) ────────────────────────────────────────────────

    @Test
    @DisplayName("registrar: crea el descanso cuando no hay uno duplicado")
    void registrar_creaDescanso() {
        LocalDate fecha = LocalDate.of(2026, 10, 5);
        when(descansoRepository.existsByEmpleado_IdempleadoAndFecha(5, fecha)).thenReturn(false);

        Map<String, Object> r = service.registrar(5, fecha, "descanso programado", "admin");

        assertThat(r.get("fecha")).isEqualTo(fecha);
        assertThat(r.get("nombreEmpleado")).isEqualTo("Juan");
    }

    @Test
    @DisplayName("registrar: ya existe un descanso en esa fecha -> 409")
    void registrar_duplicado_lanza409() {
        LocalDate fecha = LocalDate.of(2026, 10, 5);
        when(descansoRepository.existsByEmpleado_IdempleadoAndFecha(5, fecha)).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(5, fecha, null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("registrar: sin fecha -> 400")
    void registrar_sinFecha_lanza400() {
        assertThatThrownBy(() -> service.registrar(5, null, null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("registrar: un encargado de otra sucursal no puede registrar descansos de este empleado -> 403")
    void registrar_encargadoOtraSucursal_lanza403() {
        assertThatThrownBy(() -> service.registrar(5, LocalDate.now(), null, "enc9"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("registrar: el encargado de la misma sucursal sí puede")
    void registrar_encargadoMismaSucursal_permite() {
        LocalDate fecha = LocalDate.now();
        when(descansoRepository.existsByEmpleado_IdempleadoAndFecha(5, fecha)).thenReturn(false);

        Map<String, Object> r = service.registrar(5, fecha, null, "enc2");

        assertThat(r.get("iddescanso")).isEqualTo(1);
    }

    @Test
    @DisplayName("eliminar: un encargado de otra sucursal no puede eliminar -> 403")
    void eliminar_encargadoOtraSucursal_lanza403() {
        EmpleadoDescanso d = EmpleadoDescanso.builder().iddescanso(7).empleado(empleadoZocalo).fecha(LocalDate.now()).build();
        when(descansoRepository.findById(7)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.eliminar(7, "enc9"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(descansoRepository, never()).delete(any());
    }

    // ── Categoría automática por rol y sucursal ─────────────────────────────

    @Test
    @DisplayName("categoriaDe: ROOT/ADMIN es administrativo")
    void categoriaDe_admin() {
        assertThat(EmpleadoDescansoService.categoriaDe(admin)).isEqualTo(EmpleadoDescansoService.CAT_ADMINISTRATIVO);
    }

    @Test
    @DisplayName("categoriaDe: encargado de Zócalo/Superche/Corpo es categoría alta")
    void categoriaDe_encargadoTop() {
        assertThat(EmpleadoDescansoService.categoriaDe(encargadoZocalo)).isEqualTo(EmpleadoDescansoService.CAT_ENCARGADO_TOP);
        Usuario encSuperche = Usuario.builder().rol(Rol.ENCARGADO_TIENDA).tienda(superche).build();
        assertThat(EmpleadoDescansoService.categoriaDe(encSuperche)).isEqualTo(EmpleadoDescansoService.CAT_ENCARGADO_TOP);
    }

    @Test
    @DisplayName("categoriaDe: encargado de otra sucursal y vendedores/técnicos son categoría base")
    void categoriaDe_apoyo() {
        assertThat(EmpleadoDescansoService.categoriaDe(encargadoOtra)).isEqualTo(EmpleadoDescansoService.CAT_ENCARGADO_APOYO);
        Usuario vendedor = Usuario.builder().rol(Rol.VENDEDOR).tienda(zocalo).build();
        assertThat(EmpleadoDescansoService.categoriaDe(vendedor)).isEqualTo(EmpleadoDescansoService.CAT_ENCARGADO_APOYO);
    }

    @Test
    @DisplayName("maximoDe: 4 administrativos/top, 2 apoyo")
    void maximoDe_porCategoria() {
        assertThat(EmpleadoDescansoService.maximoDe(EmpleadoDescansoService.CAT_ADMINISTRATIVO)).isEqualTo(4);
        assertThat(EmpleadoDescansoService.maximoDe(EmpleadoDescansoService.CAT_ENCARGADO_TOP)).isEqualTo(4);
        assertThat(EmpleadoDescansoService.maximoDe(EmpleadoDescansoService.CAT_ENCARGADO_APOYO)).isEqualTo(2);
    }

    // ── Días ganados automáticamente ────────────────────────────────────────

    @Test
    @DisplayName("diasGanados: categoría apoyo solo gana 1 por semana, no por quincena")
    void diasGanados_apoyo_soloSemanal() {
        LocalDate corte = LocalDate.of(2026, 1, 1);
        LocalDate hoy = corte.plusDays(30); // 4 semanas completas, 2 quincenas
        assertThat(EmpleadoDescansoService.diasGanados(EmpleadoDescansoService.CAT_ENCARGADO_APOYO, corte, hoy)).isEqualTo(4);
    }

    @Test
    @DisplayName("diasGanados: administrativo/encargado top suman también el bono quincenal")
    void diasGanados_top_semanalMasQuincenal() {
        LocalDate corte = LocalDate.of(2026, 1, 1);
        LocalDate hoy = corte.plusDays(30); // 4 semanas + 2 quincenas
        assertThat(EmpleadoDescansoService.diasGanados(EmpleadoDescansoService.CAT_ADMINISTRATIVO, corte, hoy)).isEqualTo(6);
    }

    @Test
    @DisplayName("diasGanados: sin días transcurridos, no gana nada")
    void diasGanados_mismodia_cero() {
        LocalDate hoy = LocalDate.of(2026, 1, 1);
        assertThat(EmpleadoDescansoService.diasGanados(EmpleadoDescansoService.CAT_ADMINISTRATIVO, hoy, hoy)).isEqualTo(0);
    }

    // ── Saldo inicial y ajustes ──────────────────────────────────────────────

    @Test
    @DisplayName("establecerSaldoInicial: solo ROOT/ADMIN puede fijarlo")
    void establecerSaldoInicial_soloAdmin() {
        assertThatThrownBy(() -> service.establecerSaldoInicial(5, 2, LocalDate.now(), "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(saldoRepository);
    }

    @Test
    @DisplayName("establecerSaldoInicial: negativo -> 400")
    void establecerSaldoInicial_negativo_lanza400() {
        assertThatThrownBy(() -> service.establecerSaldoInicial(5, -1, LocalDate.now(), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("establecerSaldoInicial: guarda el ancla y el saldo calculado la refleja")
    void establecerSaldoInicial_guardaYCalcula() {
        LocalDate corte = LocalDate.now();
        when(saldoRepository.findById(5)).thenReturn(Optional.of(
                EmpleadoDescansoSaldo.builder().idempleado(5).saldoInicial(2).fechaCorte(corte).build()));

        Map<String, Object> r = service.establecerSaldoInicial(5, 2, corte, "admin");

        assertThat(r.get("saldoInicial")).isEqualTo(2);
        assertThat(r.get("saldoActual")).isEqualTo(2); // 0 días ganados/tomados el mismo día
        assertThat(r.get("categoria")).isEqualTo(EmpleadoDescansoService.CAT_ENCARGADO_APOYO); // Juan es VENDEDOR
    }

    @Test
    @DisplayName("registrarAjuste: solo ROOT/ADMIN")
    void registrarAjuste_soloAdmin() {
        assertThatThrownBy(() -> service.registrarAjuste(5, 2, "vacaciones", "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("registrarAjuste: sin motivo -> 400")
    void registrarAjuste_sinMotivo_lanza400() {
        assertThatThrownBy(() -> service.registrarAjuste(5, 2, "  ", "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("registrarAjuste: suma al saldo calculado")
    void registrarAjuste_sumaAlSaldo() {
        when(ajusteRepository.findByEmpleado_Idempleado(5)).thenReturn(List.of(
                EmpleadoDescansoAjuste.builder().idajuste(1).dias(2).motivo("Vacaciones por 1 año").build()));

        Map<String, Object> r = service.registrarAjuste(5, 2, "Vacaciones por 1 año", "admin");

        assertThat(r.get("ajustesTotal")).isEqualTo(2);
        assertThat(r.get("saldoActual")).isEqualTo(2); // 0 inicial + 0 ganados - 0 tomados + 2 de ajuste
    }

    @Test
    @DisplayName("saldoDe: junta inicial + ganados - tomados + ajustes, y avisa si excede el máximo")
    void saldoDe_calculoCompleto() {
        LocalDate corte = LocalDate.now().minusDays(30); // 4 semanas ganadas (apoyo: sin bono quincenal)
        when(saldoRepository.findById(5)).thenReturn(Optional.of(
                EmpleadoDescansoSaldo.builder().idempleado(5).saldoInicial(1).fechaCorte(corte).build()));
        when(descansoRepository.countByEmpleado_IdempleadoAndFechaGreaterThanEqual(5, corte)).thenReturn(1L);
        when(ajusteRepository.findByEmpleado_Idempleado(5)).thenReturn(List.of(
                EmpleadoDescansoAjuste.builder().dias(1).build()));

        Map<String, Object> r = service.saldoDe(5, "admin");

        // 1 inicial + 4 ganados - 1 tomado + 1 ajuste = 5, por encima del máximo de apoyo (2)
        assertThat(r.get("saldoActual")).isEqualTo(5);
        assertThat(r.get("excedeMaximo")).isEqualTo(true);
    }

    @Test
    @DisplayName("reporte: un encargado sin sucursal explícita -> ve la suya; de otra -> 403")
    void reporte_encargadoRestringido() {
        when(empleadoPerfilRepository.findByActivoTrueAndUsuario_Tienda_Codti(2)).thenReturn(List.of(empleadoZocalo));

        List<Map<String, Object>> r = service.reporte(2, "enc2");
        assertThat(r).hasSize(1);

        assertThatThrownBy(() -> service.reporte(9, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("reporte: sin sucursal, solo ROOT/ADMIN ve todas")
    void reporte_todasSoloAdmin() {
        assertThatThrownBy(() -> service.reporte(null, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        when(empleadoPerfilRepository.findByActivoTrue()).thenReturn(List.of(empleadoZocalo));
        assertThat(service.reporte(null, "admin")).hasSize(1);
    }
}
