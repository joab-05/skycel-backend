package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.dto.reporte.ResumenTiendaRow;
import com.skycel.backend.repository.DevolucionRepository;
import com.skycel.backend.repository.MovimientoCajaRepository;
import com.skycel.backend.repository.ProductoRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Pruebas unitarias del estado de resultados y los reportes de ventas (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class ReporteResultadosServiceTest {

    @Mock private ReporteService reporteService;
    @Mock private VentaDetalleRepository ventaDetalleRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;
    @Mock private DevolucionRepository devolucionRepository;
    @Mock private ProductoRepository productoRepository;

    private ReporteResultadosService service;
    private Tienda matriz;

    @BeforeEach
    void setUp() {
        service = new ReporteResultadosService(reporteService, ventaDetalleRepository, movimientoCajaRepository,
                devolucionRepository, productoRepository);
        matriz = Tienda.builder().codti(1).nombre("Matriz").esAlmacen(false).build();
        lenient().when(reporteService.alcance(any(), any()))
                .thenReturn(new ReporteService.Alcance(List.of(matriz), List.of(1), Map.of(1, "Matriz")));
        lenient().when(ventaDetalleRepository.resumenPorTienda(any(), any(), anyList())).thenReturn(List.of());
        lenient().when(devolucionRepository.totalDevuelto(any(), any(), anyList())).thenReturn(BigDecimal.ZERO);
        lenient().when(devolucionRepository.costoReingresado(any(), any(), anyList())).thenReturn(BigDecimal.ZERO);
        lenient().when(movimientoCajaRepository.totalesPorMotivo(any(), any(), any(), anyList())).thenReturn(List.of());
    }

    private static Object[] motivo(String nombre, int catSat, String monto) {
        return new Object[]{nombre, (byte) catSat, new BigDecimal(monto)};
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> actual(Map<String, Object> r) {
        return (Map<String, Object>) r.get("actual");
    }

    @Test
    @DisplayName("utilidad neta = ventas − devoluciones − costo (menos lo reingresado) − gastos de caja")
    void utilidadNeta() {
        LocalDate desde = LocalDate.of(2026, 10, 1), hasta = LocalDate.of(2026, 10, 31);
        when(ventaDetalleRepository.resumenPorTienda(any(), any(), anyList())).thenReturn(List.of(
                new ResumenTiendaRow(1, "Matriz", 20L, new BigDecimal("10000.00"), new BigDecimal("6000.00"))));
        when(devolucionRepository.totalDevuelto(any(), any(), anyList())).thenReturn(new BigDecimal("500.00"));
        when(devolucionRepository.costoReingresado(any(), any(), anyList())).thenReturn(new BigDecimal("300.00"));
        when(movimientoCajaRepository.totalesPorMotivo(eq((byte) 2), any(), any(), anyList())).thenReturn(List.of(
                motivo("Gasto operativo", 1, "800.00"),
                motivo("Pago de nómina", 2, "1200.00")));

        Map<String, Object> a = actual(service.estadoResultados(desde, hasta, null, "admin"));

        assertThat(a.get("ventasNetas")).isEqualTo(new BigDecimal("9500.00"));
        assertThat(a.get("costo")).isEqualTo(new BigDecimal("5700.00"));
        assertThat(a.get("utilidadBruta")).isEqualTo(new BigDecimal("3800.00"));
        assertThat(a.get("totalGastos")).isEqualTo(new BigDecimal("2000.00"));
        assertThat(a.get("utilidadNeta")).isEqualTo(new BigDecimal("1800.00"));
        assertThat(a.get("numVentas")).isEqualTo(20L);
        assertThat(a.get("margenNeto")).isEqualTo(new BigDecimal("18.9"));
    }

    @Test
    @DisplayName("retiros, traslados, pagos a proveedor, préstamos y movimientos del turno no cuentan como gasto")
    void salidasQueNoSonGasto() {
        when(movimientoCajaRepository.totalesPorMotivo(eq((byte) 2), any(), any(), anyList())).thenReturn(List.of(
                motivo("Gasto operativo", 1, "100.00"),
                motivo("Retiro de dueño", 5, "2000.00"),
                motivo("Traslado a bodega", 4, "3000.00"),
                motivo(MotivoCajaService.PAGO_PROVEEDOR, 1, "4000.00"),
                motivo(MotivoCajaService.PRESTAMO_EMPLEADO, 2, "500.00"),
                motivo(MotivoCajaService.RETIRO_CIERRE, 4, "9000.00"),
                motivo(MotivoCajaService.DEVOLUCION_VENTA, 1, "700.00")));

        Map<String, Object> a = actual(service.estadoResultados(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, "admin"));

        assertThat(a.get("totalGastos")).isEqualTo(new BigDecimal("100.00"));
        assertThat(a.get("retiros")).isEqualTo(new BigDecimal("5000.00"));
        assertThat(a.get("pagosProveedor")).isEqualTo(new BigDecimal("4000.00"));
        assertThat(a.get("prestamos")).isEqualTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("los gastos salen por concepto, del más grande al más chico")
    void gastosOrdenados() {
        when(movimientoCajaRepository.totalesPorMotivo(eq((byte) 2), any(), any(), anyList())).thenReturn(List.of(
                motivo("Papelería", 1, "50.00"), motivo("Renta", 1, "5000.00"), motivo("Flete", 1, "300.00")));

        Map<String, Object> a = actual(service.estadoResultados(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, "admin"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gastos = (List<Map<String, Object>>) a.get("gastos");
        assertThat(gastos).extracting(g -> g.get("concepto")).containsExactly("Renta", "Flete", "Papelería");
    }

    @Test
    @DisplayName("los abonos de clientes se informan aparte")
    void abonos() {
        when(movimientoCajaRepository.totalesPorMotivo(eq((byte) 1), any(), any(), anyList())).thenReturn(List.of(
                motivo(MotivoCajaService.ABONO_CUENTA, 1, "750.00"), motivo(MotivoCajaService.VENTA, 1, "9999.00")));

        Map<String, Object> a = actual(service.estadoResultados(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, "admin"));

        assertThat(a.get("abonosRecibidos")).isEqualTo(new BigDecimal("750.00"));
    }

    @Test
    @DisplayName("el periodo anterior dura lo mismo y termina el día antes")
    void periodoAnterior() {
        Map<String, Object> r = service.estadoResultados(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 20), null, "admin");

        assertThat(r.get("anteriorDesde")).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(r.get("anteriorHasta")).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    @DisplayName("sin ventas no hay división entre cero")
    void sinVentas() {
        Map<String, Object> a = actual(service.estadoResultados(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, "admin"));

        assertThat(a.get("margenBruto")).isEqualTo(BigDecimal.ZERO);
        assertThat(a.get("utilidadNeta")).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("un rango al revés -> 400")
    void rangoInvalido() {
        assertThatThrownBy(() -> service.estadoResultados(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 1), null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("ventas por día: un renglón por cada día del rango, también los vacíos")
    void ventasPorDia() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        when(ventaDetalleRepository.ventasPorDia(any(), any(), anyList())).thenReturn(List.of(
                new Object[]{d1, 3L, new BigDecimal("1000.00"), new BigDecimal("600.00")},
                new Object[]{java.sql.Date.valueOf(d1.plusDays(2)), 1L, new BigDecimal("200.00"), new BigDecimal("150.00")}));

        Map<String, Object> r = service.ventasPorDia(d1, d1.plusDays(3), null, "admin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dias = (List<Map<String, Object>>) r.get("dias");
        assertThat(dias).hasSize(4);
        assertThat(dias.get(0).get("totalVenta")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(dias.get(0).get("utilidad")).isEqualTo(new BigDecimal("400.00"));
        assertThat(dias.get(1).get("numVentas")).isEqualTo(0L);
        assertThat(dias.get(2).get("totalVenta")).isEqualTo(new BigDecimal("200.00"));
        assertThat(r.get("totalVenta")).isEqualTo(new BigDecimal("1200.00"));
        assertThat(r.get("numVentas")).isEqualTo(4L);
    }

    @Test
    @DisplayName("ventas por categoría: monto y utilidad de cada una")
    void ventasPorCategoria() {
        when(ventaDetalleRepository.ventasPorCategoria(any(), any(), anyList())).thenReturn(List.<Object[]>of(
                new Object[]{"Fundas", 10L, new BigDecimal("1500.00"), new BigDecimal("500.00")},
                new Object[]{"Sin categoría", 1L, new BigDecimal("100.00"), new BigDecimal("40.00")}));

        Map<String, Object> r = service.ventasPorCategoria(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, "admin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) r.get("categorias");
        assertThat(cats).hasSize(2);
        assertThat(cats.get(0).get("utilidad")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(r.get("totalVenta")).isEqualTo(new BigDecimal("1600.00"));
    }

    @Test
    @DisplayName("sin movimiento: suma el dinero parado a costo y trae la última venta")
    void sinMovimiento() {
        ProductoMaster funda = ProductoMaster.builder().nombreBase("Funda iPhone").build();
        Producto p = Producto.builder().codpro("F001").tienda(matriz).productoMaster(funda)
                .stock(new BigDecimal("5")).preciopro(new BigDecimal("40.00")).build();
        when(productoRepository.sinMovimiento(any(LocalDateTime.class), anyList(), any(Pageable.class))).thenReturn(List.of(p));
        LocalDateTime venta = LocalDateTime.of(2026, 8, 1, 10, 0);
        when(ventaDetalleRepository.ultimaVentaPorArticulo(anyList(), anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{"F001", 1, venta}));

        Map<String, Object> r = service.sinMovimiento(45, null, "admin");

        assertThat(r.get("dias")).isEqualTo(45);
        assertThat(r.get("valorParado")).isEqualTo(new BigDecimal("200.00"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> arts = (List<Map<String, Object>>) r.get("articulos");
        assertThat(arts.get(0).get("ultimaVenta")).isEqualTo(venta);
        assertThat(arts.get(0).get("nombreProducto")).isEqualTo("Funda iPhone");
    }

    @Test
    @DisplayName("sin movimiento con 0 días -> 400")
    void sinMovimientoDiasInvalidos() {
        assertThatThrownBy(() -> service.sinMovimiento(0, null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
