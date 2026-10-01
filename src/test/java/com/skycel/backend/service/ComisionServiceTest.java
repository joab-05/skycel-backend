package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ComisionPeriodo;
import com.skycel.backend.domain.entity.ConfigComision;
import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.comision.ComisionLineaDto;
import com.skycel.backend.dto.comision.VentaPayjoyLineaRow;
import com.skycel.backend.repository.ComisionPeriodoRepository;
import com.skycel.backend.repository.ConfigComisionRepository;
import com.skycel.backend.repository.DevolucionDetalleRepository;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.repository.VentaDetalleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de ComisionService: sin Spring ni BD, todos los repositorios mockeados. */
@ExtendWith(MockitoExtension.class)
class ComisionServiceTest {

    @Mock private ConfigComisionRepository configRepository;
    @Mock private ComisionPeriodoRepository comisionRepository;
    @Mock private VentaDetalleRepository ventaDetalleRepository;
    @Mock private TiendaRepository tiendaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmpleadoPerfilRepository empleadoPerfilRepository;
    @Mock private DevolucionDetalleRepository devolucionDetalleRepository;

    @InjectMocks
    private ComisionService service;

    private Tienda zocalo;
    private Usuario encargado, admin;
    private EmpleadoPerfil perfilEncargado;

    @BeforeEach
    void setUp() {
        zocalo = Tienda.builder().codti(2).nombre("Skycel Zocalo").activo(true).esAlmacen(false).build();
        encargado = Usuario.builder().idusuario(4).username("abigail").nombreCompleto("Abigail").rol(Rol.ENCARGADO_TIENDA)
                .tienda(zocalo).activo(true).build();
        admin = Usuario.builder().idusuario(1).username("admin").nombreCompleto("Admin").rol(Rol.ADMIN).build();
        perfilEncargado = EmpleadoPerfil.builder().idempleado(10).usuario(encargado).build();

        lenient().when(configRepository.findById(ConfigComision.ID_UNICO)).thenReturn(Optional.of(
                ConfigComision.builder().id(1).tasaEncargadoMensual(new BigDecimal("2.00"))
                        .payjoyUmbral(new BigDecimal("4000.00")).payjoyTasaBaja(new BigDecimal("2.00"))
                        .payjoyTasaAlta(new BigDecimal("1.50")).build()));
        lenient().when(configRepository.save(any(ConfigComision.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tiendaRepository.findAll()).thenReturn(List.of(zocalo));
        lenient().when(usuarioRepository.findByTienda_CodtiAndRolAndActivoTrue(2, Rol.ENCARGADO_TIENDA)).thenReturn(List.of(encargado));
        lenient().when(empleadoPerfilRepository.findByUsuarioIdusuario(4)).thenReturn(Optional.of(perfilEncargado));
        lenient().when(empleadoPerfilRepository.findById(10)).thenReturn(Optional.of(perfilEncargado));
        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(comisionRepository.save(any(ComisionPeriodo.class))).thenAnswer(inv -> {
            ComisionPeriodo c = inv.getArgument(0);
            if (c.getIdcomision() == null) c.setIdcomision(1);
            return c;
        });
        lenient().when(devolucionDetalleRepository.cantidadEnTramiteOProcesada(anyInt())).thenReturn(0L);
    }

    // ── Configuración ────────────────────────────────────────────────────────

    @Test
    @DisplayName("configuracion: si no hay fila, la crea con valores por omisión")
    void configuracion_creaPorOmision() {
        when(configRepository.findById(ConfigComision.ID_UNICO)).thenReturn(Optional.empty());

        var dto = service.configuracion();

        assertThat(dto.getTasaEncargadoMensual()).isEqualByComparingTo(ComisionService.TASA_ENCARGADO_DEFECTO);
        assertThat(dto.getPayjoyUmbral()).isEqualByComparingTo(ComisionService.PAYJOY_UMBRAL_DEFECTO);
        verify(configRepository).save(any(ConfigComision.class));
    }

    // ── Comisión de encargado ────────────────────────────────────────────────

    @Test
    @DisplayName("calcularEncargados: 2% de la venta neta (ventas menos devoluciones procesadas) de su tienda")
    void calcularEncargados_2PorcientoNeto() {
        when(ventaDetalleRepository.baseComisionEncargado(eq(2), any(), any())).thenReturn(new BigDecimal("10000"));
        when(ventaDetalleRepository.devueltoComisionEncargado(eq(2), any(), any())).thenReturn(new BigDecimal("1000"));
        when(comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(ComisionPeriodo.TIPO_ENCARGADO_MENSUAL, 10, 2026, 9))
                .thenReturn(Optional.empty());

        List<ComisionLineaDto> r = service.calcularEncargados(2026, 9);

        assertThat(r).hasSize(1);
        ComisionLineaDto linea = r.get(0);
        assertThat(linea.getNombreEmpleado()).isEqualTo("Abigail");
        assertThat(linea.getNombreTienda()).isEqualTo("Skycel Zocalo");
        assertThat(linea.getVentaBase()).isEqualByComparingTo("9000"); // 10000 - 1000
        assertThat(linea.getComision()).isEqualByComparingTo("180.00"); // 9000 * 2%
        assertThat(linea.isPagada()).isFalse();
    }

    @Test
    @DisplayName("calcularEncargados: si ya está pagada, devuelve el monto congelado sin volver a sumar ventas")
    void calcularEncargados_congeladaNoRecalcula() {
        ComisionPeriodo pagada = ComisionPeriodo.builder().idcomision(5).tipo(ComisionPeriodo.TIPO_ENCARGADO_MENSUAL)
                .empleado(perfilEncargado).tienda(zocalo).anio(2026).mes(9)
                .ventaBase(new BigDecimal("5000")).tasaAplicada(new BigDecimal("2.00")).comision(new BigDecimal("100.00"))
                .estado(ComisionPeriodo.ESTADO_PAGADA).build();
        when(comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(ComisionPeriodo.TIPO_ENCARGADO_MENSUAL, 10, 2026, 9))
                .thenReturn(Optional.of(pagada));

        List<ComisionLineaDto> r = service.calcularEncargados(2026, 9);

        assertThat(r.get(0).getComision()).isEqualByComparingTo("100.00");
        assertThat(r.get(0).isPagada()).isTrue();
        verify(ventaDetalleRepository, never()).baseComisionEncargado(any(), any(), any());
    }

    @Test
    @DisplayName("marcarPagadaEncargado: congela el monto; pagarla dos veces da 409")
    void marcarPagadaEncargado_congelaYNoDuplica() {
        when(ventaDetalleRepository.baseComisionEncargado(eq(2), any(), any())).thenReturn(new BigDecimal("1000"));
        when(ventaDetalleRepository.devueltoComisionEncargado(eq(2), any(), any())).thenReturn(BigDecimal.ZERO);
        when(comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(ComisionPeriodo.TIPO_ENCARGADO_MENSUAL, 10, 2026, 9))
                .thenReturn(Optional.empty());

        ComisionLineaDto r = service.marcarPagadaEncargado(10, 2026, 9, "admin");
        assertThat(r.isPagada()).isTrue();
        assertThat(r.getComision()).isEqualByComparingTo("20.00");

        ComisionPeriodo yaPagada = ComisionPeriodo.builder().estado(ComisionPeriodo.ESTADO_PAGADA).empleado(perfilEncargado).build();
        when(comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(ComisionPeriodo.TIPO_ENCARGADO_MENSUAL, 10, 2026, 9))
                .thenReturn(Optional.of(yaPagada));
        assertThatThrownBy(() -> service.marcarPagadaEncargado(10, 2026, 9, "admin"))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── Comisión PayJoy ──────────────────────────────────────────────────────

    @Test
    @DisplayName("calcularPayjoy: tramo bajo 2%, tramo alto 1.5%, redondeado hacia arriba al peso")
    void calcularPayjoy_tramosYRedondeo() {
        EmpleadoPerfil perfilVendedor = EmpleadoPerfil.builder().idempleado(20)
                .usuario(Usuario.builder().idusuario(7).nombreCompleto("Carlos").build()).build();
        when(empleadoPerfilRepository.findByUsuarioIdusuario(7)).thenReturn(Optional.of(perfilVendedor));
        when(comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(ComisionPeriodo.TIPO_VENDEDOR_PAYJOY, 20, 2026, 9))
                .thenReturn(Optional.empty());

        List<VentaPayjoyLineaRow> lineas = List.of(
                new VentaPayjoyLineaRow(7, "Carlos", 101, new BigDecimal("3999")),   // <4000: 2% de 3999 = 79.98 -> 80
                new VentaPayjoyLineaRow(7, "Carlos", 102, new BigDecimal("4000")));  // >=4000: 1.5% de 4000 = 60.00 -> 60
        when(ventaDetalleRepository.lineasPayjoy(any(), any())).thenReturn(lineas);

        List<ComisionLineaDto> r = service.calcularPayjoy(2026, 9);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).getNombreEmpleado()).isEqualTo("Carlos");
        assertThat(r.get(0).getVentaBase()).isEqualByComparingTo("7999");
        assertThat(r.get(0).getComision()).isEqualByComparingTo("140"); // 80 + 60
    }

    @Test
    @DisplayName("calcularPayjoy: una línea con devolución en trámite o procesada no comisiona")
    void calcularPayjoy_excluyeDevuelta() {
        EmpleadoPerfil perfilVendedor = EmpleadoPerfil.builder().idempleado(20)
                .usuario(Usuario.builder().idusuario(7).nombreCompleto("Carlos").build()).build();
        when(empleadoPerfilRepository.findByUsuarioIdusuario(7)).thenReturn(Optional.of(perfilVendedor));
        when(comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(ComisionPeriodo.TIPO_VENDEDOR_PAYJOY, 20, 2026, 9))
                .thenReturn(Optional.empty());
        when(ventaDetalleRepository.lineasPayjoy(any(), any())).thenReturn(List.of(
                new VentaPayjoyLineaRow(7, "Carlos", 101, new BigDecimal("3000"))));
        when(devolucionDetalleRepository.cantidadEnTramiteOProcesada(101)).thenReturn(1L);

        List<ComisionLineaDto> r = service.calcularPayjoy(2026, 9);

        assertThat(r.get(0).getVentaBase()).isEqualByComparingTo("0");
        assertThat(r.get(0).getComision()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("marcarPagadaPayjoy: congela el total de ese vendedor en el mes")
    void marcarPagadaPayjoy_congela() {
        EmpleadoPerfil perfilVendedor = EmpleadoPerfil.builder().idempleado(20)
                .usuario(Usuario.builder().idusuario(7).nombreCompleto("Carlos").build()).build();
        when(empleadoPerfilRepository.findById(20)).thenReturn(Optional.of(perfilVendedor));
        when(comisionRepository.findByTipoAndEmpleado_IdempleadoAndAnioAndMes(ComisionPeriodo.TIPO_VENDEDOR_PAYJOY, 20, 2026, 9))
                .thenReturn(Optional.empty());
        when(ventaDetalleRepository.lineasPayjoy(any(), any())).thenReturn(List.of(
                new VentaPayjoyLineaRow(7, "Carlos", 101, new BigDecimal("2000"))));

        ComisionLineaDto r = service.marcarPagadaPayjoy(20, 2026, 9, "admin");

        assertThat(r.isPagada()).isTrue();
        assertThat(r.getComision()).isEqualByComparingTo("40"); // 2% de 2000
    }
}
