package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.caja.DetalleCorteDto;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/** El detalle de un turno para auditarlo: ventas con sus artículos, equipos contado/PayJoy y movimientos con su origen. */
@ExtendWith(MockitoExtension.class)
class DetalleCorteServiceTest {

    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private VentaRepository ventaRepository;
    @Mock private VentaDetalleRepository ventaDetalleRepository;
    @Mock private VentaPagoDetalleRepository ventaPagoDetalleRepository;
    @Mock private MovimientoCajaRepository movimientoCajaRepository;

    private DetalleCorteService service;
    private Tienda zocalo, corpo;
    private Usuario root, luis, encargadoCorpo, ana;
    private final LocalDateTime apertura = LocalDateTime.of(2026, 10, 29, 9, 0);

    @BeforeEach
    void setUp() {
        service = new DetalleCorteService(sesionCajaRepository, usuarioRepository, ventaRepository, ventaDetalleRepository,
                ventaPagoDetalleRepository, movimientoCajaRepository);
        zocalo = Tienda.builder().codti(2).nombre("Zocalo").build();
        corpo = Tienda.builder().codti(3).nombre("Corpo").build();
        Caja caja = Caja.builder().idCaja(1).nombreCaja("Caja Zocalo").tienda(zocalo).build();
        root = Usuario.builder().idusuario(1).username("root").nombreCompleto("Root").rol(Rol.ROOT).tienda(zocalo).build();
        luis = Usuario.builder().idusuario(4).username("luis").nombreCompleto("Luis").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        encargadoCorpo = Usuario.builder().idusuario(5).username("eva").nombreCompleto("Eva").rol(Rol.ENCARGADO_TIENDA).tienda(corpo).build();
        ana = Usuario.builder().idusuario(6).username("ana").nombreCompleto("Ana").rol(Rol.VENDEDOR).tienda(zocalo).build();
        for (Usuario u : List.of(root, luis, encargadoCorpo)) lenient().when(usuarioRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));

        SesionCaja s = SesionCaja.builder().idsesionCaja(7).caja(caja).usuarioApertura(luis).estado(SesionCaja.CERRADA)
                .fechaApertura(apertura).fechaCierre(apertura.plusHours(10)).fondoInicial(new BigDecimal("500")).build();
        lenient().when(sesionCajaRepository.findById(7)).thenReturn(Optional.of(s));

        ProductoMaster funda = ProductoMaster.builder().nombreBase("Funda Silicon A15").build();
        ProductoMaster celular = ProductoMaster.builder().nombreBase("Samsung A15 128gb").build();

        Venta v1 = venta(10, 1, "Efectivo", "149", 1, apertura.plusHours(1), null);
        Venta v2 = venta(11, 1, "Efectivo", "3599", 1, apertura.plusHours(2), null);
        Venta v3 = venta(12, 5, "PayJoy", "3599", 1, apertura.plusHours(3), null);
        Venta v4 = venta(13, 1, "Efectivo", "99", 2, apertura.plusHours(4), null);
        lenient().when(ventaRepository.findByCaja_IdCajaAndFechaVentaBetween(eq(1), any(), any())).thenReturn(List.of(v3, v1, v4, v2));
        lenient().when(ventaDetalleRepository.findByVenta_Idventa(10)).thenReturn(List.of(linea(funda, "FUN-1", null, "149", 1, null)));
        lenient().when(ventaDetalleRepository.findByVenta_Idventa(11)).thenReturn(List.of(linea(celular, "EQ1", "350000000000011", "3599", 1, null)));
        lenient().when(ventaDetalleRepository.findByVenta_Idventa(12)).thenReturn(List.of(linea(celular, "EQ1", "350000000000012", "3599", 1, new BigDecimal("800"))));
        lenient().when(ventaDetalleRepository.findByVenta_Idventa(13)).thenReturn(List.of(linea(funda, "FUN-1", null, "99", 1, null)));
        lenient().when(ventaPagoDetalleRepository.findByVenta_IdventaOrderByIdpagoDetalle(any())).thenReturn(List.of());

        lenient().when(movimientoCajaRepository.findByCaja_IdCajaAndFechaMovBetweenOrderByFechaMovDesc(eq(1), any(), any())).thenReturn(List.of(
                mov(3, 2, "Gasto de tienda", "30", "Garrafón de agua", apertura.plusHours(5)),
                mov(4, 1, "Venta", "3000", "Venta #11", apertura.plusHours(2)),
                mov(2, 1, "Venta", "149", "Venta #10", apertura.plusHours(1)),
                mov(1, 1, "Fondo de apertura", "500", null, apertura)));
    }

    private Venta venta(int id, int metodo, String nombreMetodo, String total, int estado, LocalDateTime fecha, String claveOffline) {
        return Venta.builder().idventa(id).usuarioVendedor(ana).metodoPago((byte) metodo).total(new BigDecimal(total)).estado((byte) estado)
                .fechaVenta(fecha).claveOffline(claveOffline).build();
    }

    private VentaDetalle linea(ProductoMaster m, String codpro, String imei, String precio, int cantidad, BigDecimal enganche) {
        return VentaDetalle.builder().productoMaster(m).codpro(codpro).imei(imei).precioUnitarioFinal(new BigDecimal(precio))
                .cantidad((short) cantidad).enganchePayjoy(enganche).plazoMesesPayjoy(enganche == null ? null : 6).build();
    }

    private MovimientoCaja mov(int id, int tipo, String motivo, String monto, String nota, LocalDateTime fecha) {
        return MovimientoCaja.builder().idmovimiento(id).tipo((byte) tipo).motivo(CatMotivo.builder().nombre(motivo).build())
                .monto(new BigDecimal(monto)).observaciones(nota).fechaMov(fecha).usuarioEncargado(luis).build();
    }

    @Test
    @DisplayName("Lista todas las ventas en orden, con vendedor, artículos y las canceladas marcadas")
    void listaVentas() {
        DetalleCorteDto d = service.detalle(7, "luis");

        assertThat(d.getVentas()).extracting(DetalleCorteDto.VentaDto::getIdventa).containsExactly(10, 11, 12, 13);
        assertThat(d.getVentas().get(0).getVendedor()).isEqualTo("Ana");
        assertThat(d.getVentas().get(0).getArticulos().get(0).getNombre()).isEqualTo("Funda Silicon A15");
        assertThat(d.getVentas().get(3).isCompletada()).isFalse();
        assertThat(d.getVentas().get(2).getMetodo()).isEqualTo("PayJoy");
    }

    @Test
    @DisplayName("Los equipos salen con su IMEI y se cuentan al contado o con PayJoy (las canceladas no cuentan)")
    void equipos() {
        DetalleCorteDto d = service.detalle(7, "luis");

        DetalleCorteDto.ArticuloDto contado = d.getVentas().get(1).getArticulos().get(0);
        assertThat(contado.isEquipo()).isTrue();
        assertThat(contado.getImei()).isEqualTo("350000000000011");
        assertThat(contado.isPayjoy()).isFalse();
        DetalleCorteDto.ArticuloDto conPayjoy = d.getVentas().get(2).getArticulos().get(0);
        assertThat(conPayjoy.isPayjoy()).isTrue();
        assertThat(conPayjoy.getEnganche()).isEqualByComparingTo("800");
        assertThat(conPayjoy.getPlazoMeses()).isEqualTo(6);

        assertThat(d.getEquipos().getContado()).isEqualTo(1);
        assertThat(d.getEquipos().getPayjoy()).isEqualTo(1);
        assertThat(d.getEquipos().getImporteContado()).isEqualByComparingTo("3599");
        assertThat(d.getEquipos().getEnganchesPayjoy()).isEqualByComparingTo("800");
    }

    @Test
    @DisplayName("Los movimientos salen en orden con su origen: turno, venta u otro")
    void movimientos() {
        DetalleCorteDto d = service.detalle(7, "luis");

        assertThat(d.getMovimientos()).extracting(DetalleCorteDto.MovimientoDto::getIdmovimiento).containsExactly(1, 2, 4, 3);
        assertThat(d.getMovimientos()).extracting(DetalleCorteDto.MovimientoDto::getOrigen).containsExactly("TURNO", "VENTA", "VENTA", "OTRO");
        DetalleCorteDto.MovimientoDto gasto = d.getMovimientos().get(3);
        assertThat(gasto.getTipo()).isEqualTo(2);
        assertThat(gasto.getObservaciones()).isEqualTo("Garrafón de agua");
        assertThat(gasto.getUsuario()).isEqualTo("Luis");
    }

    @Test
    @DisplayName("Marca la venta en efectivo cuyo dinero no coincide con lo registrado en la caja")
    void alertasDeEfectivo() {
        DetalleCorteDto d = service.detalle(7, "luis");

        assertThat(d.getVentas().get(0).getAlerta()).isNull();                       // #10: 149 y la caja registró 149
        assertThat(d.getVentas().get(0).getEfectivoEnCaja()).isEqualByComparingTo("149");
        assertThat(d.getVentas().get(1).getAlerta()).contains("3599.00").contains("3000.00");   // #11: debió dejar 3599, hay 3000
        assertThat(d.getVentas().get(2).getAlerta()).isNull();                       // #12 con PayJoy: no se puede saber
        assertThat(d.getVentas().get(3).getAlerta()).isNull();                       // #13 cancelada: no se revisa
        assertThat(d.getVentasConAlerta()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un encargado no ve los cortes de otra tienda; el administrador sí")
    void acceso() {
        assertThatThrownBy(() -> service.detalle(7, "eva"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service.detalle(7, "root").getIdsesionCaja()).isEqualTo(7);
    }

    @Test
    @DisplayName("Un turno que no existe da 404")
    void noExiste() {
        assertThatThrownBy(() -> service.detalle(99, "root"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
