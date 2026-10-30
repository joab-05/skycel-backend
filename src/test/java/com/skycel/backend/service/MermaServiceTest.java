package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.producto.MermaReporteDto;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/** Control de mermas: qué cuenta, cómo se clasifica, cuánto vale y quién lo puede ver. */
@ExtendWith(MockitoExtension.class)
class MermaServiceTest {

    @Mock private MovimientoInventarioRepository movimientoRepository;
    @Mock private ProductoImeiRepository imeiRepository;
    @Mock private TiendaRepository tiendaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PermisoService permisoService;

    private MermaService service;
    private Usuario admin, luis;

    @BeforeEach
    void setUp() {
        service = new MermaService(movimientoRepository, imeiRepository, tiendaRepository, usuarioRepository, permisoService);
        Tienda zocalo = Tienda.builder().codti(2).nombre("Zocalo").build();
        Tienda corpo = Tienda.builder().codti(3).nombre("Corpo").build();
        admin = Usuario.builder().idusuario(1).username("admin").nombreCompleto("Admin").rol(Rol.ADMIN).tienda(zocalo).build();
        luis = Usuario.builder().idusuario(4).username("luis").nombreCompleto("Luis").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("luis")).thenReturn(Optional.of(luis));
        lenient().when(tiendaRepository.findAll()).thenReturn(List.of(zocalo, corpo));
        lenient().when(permisoService.tiene(any(), eq("COSTOS_VER"))).thenReturn(true);

        ProductoMaster mica = ProductoMaster.builder().nombreBase("Mica Cristal").build();
        Producto p = Producto.builder().codpro("MIC-1").productoMaster(mica).preciopro(new BigDecimal("20")).build();
        ProductoMaster cel = ProductoMaster.builder().nombreBase("Samsung A15").build();
        lenient().when(imeiRepository.findByImei("350000000000011")).thenReturn(Optional.of(
                ProductoImei.builder().imei("350000000000011").costo(new BigDecimal("2500")).build()));
        lenient().when(movimientoRepository.mermas(any(), any(), any(), any())).thenReturn(List.of(
                MovimientoInventario.builder().idmov(1L).producto(p).codti(2).tipo("SALIDA").cantidad(new BigDecimal("-3")).motivo("Robo - se llevaron 3")
                        .usuario(luis).fecha(LocalDateTime.now()).build(),
                MovimientoInventario.builder().idmov(2L).imei("350000000000011").productoMaster(cel).codti(2).tipo("SALIDA").cantidad(new BigDecimal("-1"))
                        .motivo("se cayó y no prende").usuario(luis).fecha(LocalDateTime.now()).build(),
                MovimientoInventario.builder().idmov(3L).producto(p).codti(3).tipo("AJUSTE").cantidad(new BigDecimal("2"))
                        .motivo("Ajuste por inventario físico #4").usuario(admin).fecha(LocalDateTime.now()).build()));
    }

    @Test
    @DisplayName("clasifica por el motivo y valúa a costo: pérdida, sobrante y resumen por categoría y usuario")
    void reporteCompleto() {
        MermaReporteDto r = service.reporte(null, null, null, "admin");

        assertThat(r.getMovimientos()).isEqualTo(3);
        assertThat(r.getPerdida()).isEqualByComparingTo("2560");      // 3 × 20 + 1 × 2500
        assertThat(r.getSobrante()).isEqualByComparingTo("40");       // 2 × 20
        assertThat(r.getDetalle()).extracting(d -> d.getCategoria())
                .containsExactly("Robo o extravío", "Otro motivo", "Ajuste por inventario físico");
        assertThat(r.getPorUsuario()).extracting(x -> x.getNombre()).contains("Luis", "Admin");
        assertThat(r.getPorUsuario().get(0).getNombre()).isEqualTo("Luis");   // el que más pesa va primero
    }

    @Test
    @DisplayName("un encargado solo ve su tienda aunque pida otra")
    void encargadoSoloSuTienda() {
        service.reporte(3, null, null, "luis");

        verify(movimientoRepository).mermas(eq(2), any(), any(), any());
    }

    @Test
    @DisplayName("sin permiso de costos los valores van vacíos")
    void sinCostos() {
        lenient().when(permisoService.tiene(any(), eq("COSTOS_VER"))).thenReturn(false);

        MermaReporteDto r = service.reporte(null, null, null, "luis");

        assertThat(r.isConCostos()).isFalse();
        assertThat(r.getPerdida()).isNull();
        assertThat(r.getDetalle()).allSatisfy(d -> assertThat(d.getValor()).isNull());
    }

    @Test
    @DisplayName("la categoría sale del texto del motivo")
    void categorias() {
        assertThat(MermaService.categoria("SALIDA", "Merma/Daño - se mojó")).isEqualTo("Daño o merma");
        assertThat(MermaService.categoria("SALIDA", "Muestra/Regalo")).isEqualTo("Muestra, regalo o consumo");
        assertThat(MermaService.categoria("SALIDA", "Transferencia - a Corpo")).isEqualTo("Salida por transferencia (fuera de traspasos)");
        assertThat(MermaService.categoria("AJUSTE", "Ajuste de inventario")).isEqualTo("Ajuste manual de inventario");
        assertThat(MermaService.categoria("SALIDA", "robado en vitrina")).isEqualTo("Otro motivo");
        assertThat(MermaService.categoria("SALIDA", "error de captura")).isEqualTo("Error de captura");
        assertThat(MermaService.categoria("SALIDA", null)).isEqualTo("Otro motivo");
    }
}
