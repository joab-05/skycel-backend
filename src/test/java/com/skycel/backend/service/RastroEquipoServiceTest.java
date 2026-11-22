package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.dto.equipo.EquipoResponseDto;
import com.skycel.backend.dto.equipo.RastroEquipoDto;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rastreo de un equipo")
class RastroEquipoServiceTest {

    private static final String IMEI = "860534071424144";

    @Mock private EquipoService equipoService;
    @Mock private MovimientoInventarioRepository movimientoRepository;
    @Mock private TiendaRepository tiendaRepository;
    @Mock private VentaRepository ventaRepository;
    @Mock private VentaDetalleRepository ventaDetalleRepository;
    @Mock private TraspasoDetalleRepository traspasoDetalleRepository;
    @Mock private GarantiaRepository garantiaRepository;
    @InjectMocks private RastroEquipoService service;

    private final Tienda bodega = Tienda.builder().codti(9).nombre("Bodega central").build();
    private final Tienda zocalo = Tienda.builder().codti(1).nombre("Zócalo").build();
    private final Tienda corpo = Tienda.builder().codti(3).nombre("Corpo").build();

    private MovimientoInventario mov(String tipo, int cantidad, Tienda t, LocalDateTime fecha, String motivo, String ref) {
        return MovimientoInventario.builder().imei(IMEI).tipo(tipo).cantidad(BigDecimal.valueOf(cantidad)).stockAntes(BigDecimal.ZERO)
                .stockDespues(BigDecimal.ONE).codti(t.getCodti()).fecha(fecha).motivo(motivo).referencia(ref)
                .usuario(Usuario.builder().nombreCompleto("Joab Duarte").build()).build();
    }

    private void base(EquipoResponseDto equipo) {
        lenient().when(equipoService.porImei(anyString(), anyString())).thenReturn(equipo);
        lenient().when(tiendaRepository.findAll()).thenReturn(List.of(bodega, zocalo, corpo));
        lenient().when(traspasoDetalleRepository.findByImei(IMEI)).thenReturn(List.of());
        lenient().when(ventaDetalleRepository.findByImei(IMEI)).thenReturn(List.of());
        lenient().when(garantiaRepository.findByImei(IMEI)).thenReturn(List.of());
    }

    @Test
    @DisplayName("junta en orden el ingreso, el traspaso (de dónde a dónde) y la venta (a quién), y calcula el recorrido")
    void recorridoCompleto() {
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 1, 10, 0);
        base(EquipoResponseDto.builder().imei(IMEI).estado("VENDIDO").fechaRegistro(t0).build());

        Traspaso traspaso = Traspaso.builder().idtraspaso(16).tiendaOrigen(bodega).tiendaDestino(zocalo).build();
        when(traspasoDetalleRepository.findByImei(IMEI)).thenReturn(List.of(TraspasoDetalle.builder().traspaso(traspaso).imei(IMEI).build()));
        Venta venta = Venta.builder().idventa(77).cliente(Cliente.builder().nombreCompleto("Luis Pérez").build())
                .usuarioVendedor(Usuario.builder().nombreCompleto("Abigail Portillo").build()).build();
        when(ventaDetalleRepository.findByImei(IMEI)).thenReturn(List.of(VentaDetalle.builder().venta(venta).imei(IMEI)
                .precioUnitarioFinal(new BigDecimal("3499")).build()));
        when(movimientoRepository.rastro(IMEI)).thenReturn(List.of(
                mov("ALTA", 1, bodega, t0, "Compra a proveedor", null),
                mov("TRASPASO_SALIDA", -1, bodega, t0.plusDays(2), null, "Traspaso #16"),
                mov("TRASPASO_ENTRADA", 1, zocalo, t0.plusDays(2).plusHours(5), null, "Traspaso #16"),
                mov("VENTA", -1, zocalo, t0.plusDays(10), null, "Venta #77")));

        RastroEquipoDto r = service.rastro(IMEI, "admin");

        assertThat(r.getEventos()).extracting(RastroEquipoDto.EventoDto::getTipo)
                .containsExactly("ALTA", "TRASPASO_SALIDA", "TRASPASO_ENTRADA", "VENTA");
        assertThat(r.getEventos().get(1).getDetalle()).isEqualTo("Va a Zócalo");
        assertThat(r.getEventos().get(2).getDetalle()).isEqualTo("Viene de Bodega central");
        assertThat(r.getEventos().get(3).getDetalle()).contains("Luis Pérez").contains("Abigail Portillo");
        assertThat(r.getEventos().get(3).getMonto()).isEqualByComparingTo("3499");
        assertThat(r.getRecorrido()).containsExactly("Bodega central", "Zócalo");
        assertThat(r.getTraspasos()).isEqualTo(1);
        assertThat(r.isVendido()).isTrue();
        assertThat(r.getDiasEnInventario()).isEqualTo(10);   // de su ingreso a su venta
    }

    @Test
    @DisplayName("un equipo registrado antes de que hubiera historial arranca con «Se registró en el sistema»")
    void sinAlta() {
        LocalDateTime t0 = LocalDateTime.of(2026, 9, 1, 9, 0);
        base(EquipoResponseDto.builder().imei(IMEI).estado("DISPONIBLE").fechaRegistro(t0).nombreProveedor("Telcel Mayoreo").build());
        when(movimientoRepository.rastro(IMEI)).thenReturn(List.of());

        RastroEquipoDto r = service.rastro(IMEI, "admin");

        assertThat(r.getEventos()).hasSize(1);
        assertThat(r.getEventos().get(0).getTitulo()).isEqualTo("Se registró en el sistema");
        assertThat(r.getEventos().get(0).getDetalle()).contains("Telcel Mayoreo");
        assertThat(r.isVendido()).isFalse();
    }

    @Test
    @DisplayName("la baja y la reactivación quedan en la línea; una venta cancelada o devuelta deja de contar como vendido")
    void bajaYReactivacion() {
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 1, 10, 0);
        base(EquipoResponseDto.builder().imei(IMEI).estado("DISPONIBLE").fechaRegistro(t0).build());
        when(movimientoRepository.rastro(IMEI)).thenReturn(List.of(
                mov("ALTA", 1, zocalo, t0, null, null),
                mov("VENTA", -1, zocalo, t0.plusDays(1), null, "Venta #1"),
                mov("DEVOLUCION", 1, zocalo, t0.plusDays(3), "Devolución de venta", "DV-0001"),
                mov("SALIDA", -1, zocalo, t0.plusDays(4), "Error de captura", null),
                mov("AJUSTE", 1, corpo, t0.plusDays(5), "Reactivado (antes: DEBAJA)", null)));

        RastroEquipoDto r = service.rastro(IMEI, "admin");

        assertThat(r.isVendido()).isFalse();
        assertThat(r.getEventos().get(3).getTitulo()).startsWith("Dado de baja");
        assertThat(r.getEventos().get(4).getTitulo()).contains("volvió a estar disponible en Corpo");
        assertThat(r.getRecorrido()).containsExactly("Zócalo", "Corpo");
    }

    @Test
    @DisplayName("los reclamos de garantía aparecen en su fecha, con su estado actual")
    void garantia() {
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 1, 10, 0);
        base(EquipoResponseDto.builder().imei(IMEI).estado("EN_GARANTIA").fechaRegistro(t0).build());
        when(movimientoRepository.rastro(IMEI)).thenReturn(List.of(mov("ALTA", 1, zocalo, t0, null, null)));
        when(garantiaRepository.findByImei(IMEI)).thenReturn(List.of(Garantia.builder().folioSeguimiento("GAR-0041")
                .fechaIngreso(t0.plusDays(5)).fallaReportada("No enciende").estadoActual((byte) 5).tienda(zocalo).build()));

        RastroEquipoDto r = service.rastro(IMEI, "admin");

        assertThat(r.getEventos()).hasSize(2);
        assertThat(r.getEventos().get(1).getTitulo()).isEqualTo("Reclamo de garantía GAR-0041");
        assertThat(r.getEventos().get(1).getDetalle()).contains("No enciende").contains("en el proveedor");
        assertThat(r.getGarantias()).isEqualTo(1);
    }
}
