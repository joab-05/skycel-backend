package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.reporte.ResumenTiendaRow;
import com.skycel.backend.dto.reporte.TopProductoRow;
import com.skycel.backend.dto.reporte.ValorInventarioRow;
import com.skycel.backend.repository.ProductoRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.repository.VentaDetalleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de los reportes gerenciales (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock private VentaDetalleRepository ventaDetalleRepository;
    @Mock private ProductoRepository     productoRepository;
    @Mock private TiendaRepository       tiendaRepository;
    @Mock private UsuarioRepository      usuarioRepository;

    private ReporteService service;

    private Tienda matriz, zocalo, almacen;
    private Usuario admin, encargadoZocalo;

    @BeforeEach
    void setUp() {
        service = new ReporteService(ventaDetalleRepository, productoRepository, tiendaRepository, usuarioRepository);
        matriz = Tienda.builder().codti(1).nombre("Matriz").esAlmacen(false).build();
        zocalo = Tienda.builder().codti(2).nombre("Zócalo").esAlmacen(false).build();
        almacen = Tienda.builder().codti(9).nombre("Bodega").esAlmacen(true).build();
        admin = Usuario.builder().idusuario(1).username("admin").rol(Rol.ADMIN).build();
        encargadoZocalo = Usuario.builder().idusuario(2).username("enc2").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();

        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("enc2")).thenReturn(Optional.of(encargadoZocalo));
        lenient().when(tiendaRepository.findAll()).thenReturn(List.of(matriz, zocalo, almacen));
        lenient().when(tiendaRepository.findById(1)).thenReturn(Optional.of(matriz));
        lenient().when(tiendaRepository.findById(2)).thenReturn(Optional.of(zocalo));
        lenient().when(ventaDetalleRepository.resumenPorTienda(any(), any(), anyList())).thenReturn(List.of());
        lenient().when(ventaDetalleRepository.topPorCantidad(any(), any(), anyList(), any(Pageable.class))).thenReturn(List.of());
        lenient().when(ventaDetalleRepository.topPorMonto(any(), any(), anyList(), any(Pageable.class))).thenReturn(List.of());
        lenient().when(productoRepository.valorInventarioPorTienda(anyList())).thenReturn(List.of());
    }

    @Test
    @DisplayName("un encargado sin tienda fija -> 400")
    void encargadoSinTienda_lanza400() {
        Usuario sinTienda = Usuario.builder().idusuario(3).username("sinT").rol(Rol.ENCARGADO_TIENDA).build();
        when(usuarioRepository.findByUsername("sinT")).thenReturn(Optional.of(sinTienda));

        assertThatThrownBy(() -> service.gerencial(null, null, null, "sinT"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("un encargado que pide otra sucursal -> 403")
    void encargadoOtraTienda_lanza403() {
        assertThatThrownBy(() -> service.gerencial(null, null, 1, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("un encargado ve solo su tienda: no es comparativo")
    void encargado_soloSuTienda() {
        Map<String, Object> r = service.gerencial(null, null, null, "enc2");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porTienda = (List<Map<String, Object>>) r.get("porTienda");
        assertThat(porTienda).hasSize(1);
        assertThat(porTienda.get(0).get("codti")).isEqualTo(2);
        assertThat(r.get("comparativo")).isEqualTo(false);
        verify(ventaDetalleRepository).resumenPorTienda(any(), any(), eq2(2));
    }

    @Test
    @DisplayName("un administrador sin sucursal ve el comparativo de todas (sin el almacén)")
    void admin_sinCodti_todasMenosAlmacen() {
        Map<String, Object> r = service.gerencial(null, null, null, "admin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porTienda = (List<Map<String, Object>>) r.get("porTienda");
        assertThat(porTienda).extracting(m -> m.get("codti")).containsExactlyInAnyOrder(1, 2);
        assertThat(r.get("comparativo")).isEqualTo(true);
    }

    @Test
    @DisplayName("un administrador puede pedir una sola sucursal")
    void admin_conCodti_unaSola() {
        Map<String, Object> r = service.gerencial(null, null, 1, "admin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porTienda = (List<Map<String, Object>>) r.get("porTienda");
        assertThat(porTienda).hasSize(1);
        assertThat(porTienda.get(0).get("codti")).isEqualTo(1);
    }

    @Test
    @DisplayName("la fecha 'desde' posterior a 'hasta' -> 400")
    void fechaDesdeDespuesDeHasta_lanza400() {
        assertThatThrownBy(() -> service.gerencial(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1), null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("una sucursal sin ventas en el rango aparece con ceros, no desaparece")
    void tiendaSinVentas_apareceConCeros() {
        when(ventaDetalleRepository.resumenPorTienda(any(), any(), anyList()))
                .thenReturn(List.of(new ResumenTiendaRow(1, "Matriz", 3L, new BigDecimal("900.00"), new BigDecimal("600.00"))));

        Map<String, Object> r = service.gerencial(null, null, null, "admin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porTienda = (List<Map<String, Object>>) r.get("porTienda");
        Map<String, Object> zocaloFila = porTienda.stream().filter(m -> m.get("codti").equals(2)).findFirst().orElseThrow();
        assertThat(zocaloFila.get("numVentas")).isEqualTo(0L);
        assertThat(zocaloFila.get("totalVenta")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("la utilidad y el margen se calculan a partir de venta y costo")
    void utilidadYMargen_seCalculan() {
        when(ventaDetalleRepository.resumenPorTienda(any(), any(), anyList()))
                .thenReturn(List.of(new ResumenTiendaRow(1, "Matriz", 4L, new BigDecimal("1000.00"), new BigDecimal("700.00"))));

        Map<String, Object> r = service.gerencial(null, null, 1, "admin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porTienda = (List<Map<String, Object>>) r.get("porTienda");
        assertThat((BigDecimal) porTienda.get(0).get("utilidad")).isEqualByComparingTo("300.00");
        assertThat((BigDecimal) porTienda.get(0).get("margenPorciento")).isEqualByComparingTo("30.0");
        assertThat((BigDecimal) r.get("totalUtilidad")).isEqualByComparingTo("300.00");
        assertThat((BigDecimal) r.get("margenPorciento")).isEqualByComparingTo("30.0");
    }

    @Test
    @DisplayName("sin ventas en el rango, el margen es 0 y no truena por división entre cero")
    void sinVentas_margenCero() {
        Map<String, Object> r = service.gerencial(null, null, 1, "admin");

        assertThat((BigDecimal) r.get("margenPorciento")).isEqualByComparingTo("0");
        assertThat((BigDecimal) r.get("totalVenta")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("trae los artículos más vendidos por cantidad y por monto")
    void topProductos_sePropagan() {
        when(ventaDetalleRepository.topPorCantidad(any(), any(), anyList(), any(Pageable.class)))
                .thenReturn(List.of(new TopProductoRow(10, "Mica Cristal", 40L, new BigDecimal("2000.00"))));
        when(ventaDetalleRepository.topPorMonto(any(), any(), anyList(), any(Pageable.class)))
                .thenReturn(List.of(new TopProductoRow(20, "iPhone 13", 5L, new BigDecimal("50000.00"))));

        Map<String, Object> r = service.gerencial(null, null, 1, "admin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porCantidad = (List<Map<String, Object>>) r.get("topPorCantidad");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porMonto = (List<Map<String, Object>>) r.get("topPorMonto");
        assertThat(porCantidad.get(0).get("nombreProducto")).isEqualTo("Mica Cristal");
        assertThat(porMonto.get(0).get("nombreProducto")).isEqualTo("iPhone 13");
    }

    @Test
    @DisplayName("el valor del inventario se suma entre las sucursales del alcance")
    void valorInventario_seSuma() {
        when(productoRepository.valorInventarioPorTienda(anyList())).thenReturn(List.of(
                new ValorInventarioRow(1, "Matriz", new BigDecimal("10000"), new BigDecimal("18000"), 50L),
                new ValorInventarioRow(2, "Zócalo", new BigDecimal("30000"), new BigDecimal("54000"), 120L)));

        Map<String, Object> r = service.gerencial(null, null, null, "admin");

        assertThat((BigDecimal) r.get("inventarioValorCosto")).isEqualByComparingTo("40000");
        assertThat((BigDecimal) r.get("inventarioValorVenta")).isEqualByComparingTo("72000");
    }

    /** Mockito matcher chico: una lista que contiene exactamente ese único codti. */
    private static List<Integer> eq2(Integer codti) {
        return argThat(l -> l != null && l.size() == 1 && l.get(0).equals(codti));
    }
}
