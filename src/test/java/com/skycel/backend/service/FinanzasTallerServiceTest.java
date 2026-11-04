package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ComisionTallerDescuento;
import com.skycel.backend.domain.entity.ComisionTallerTecnico;
import com.skycel.backend.domain.entity.Gasto;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.finanzastaller.GastoRequestDto;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/** Las finanzas del taller, con los números de la libreta de octubre de 2026. */
@ExtendWith(MockitoExtension.class)
class FinanzasTallerServiceTest {

    @Mock private OrdenServicioRepository ordenRepository;
    @Mock private GastoRepository gastoRepository;
    @Mock private NominaDetalleRepository nominaDetalleRepository;
    @Mock private ComisionTallerTecnicoRepository comisionRepository;
    @Mock private ComisionTallerDescuentoRepository descuentoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TiendaRepository tiendaRepository;

    private FinanzasTallerService service;
    private Usuario alex, oswaldo, mike;

    @BeforeEach
    void preparar() {
        service = new FinanzasTallerService(ordenRepository, gastoRepository, nominaDetalleRepository,
                comisionRepository, descuentoRepository, usuarioRepository, tiendaRepository);
        alex = tecnico(10, "Alex");
        oswaldo = tecnico(11, "Oswaldo");
        mike = tecnico(12, "Mike");
        lenient().when(usuarioRepository.findByUsername("root")).thenReturn(java.util.Optional.of(
                Usuario.builder().idusuario(1).username("root").rol(Rol.ROOT).activo(true).build()));
        lenient().when(usuarioRepository.findByUsername("vendedor")).thenReturn(java.util.Optional.of(
                Usuario.builder().idusuario(2).username("vendedor").rol(Rol.VENDEDOR).activo(true).build()));
        lenient().when(usuarioRepository.findByRolAndActivoTrue(Rol.TECNICO)).thenReturn(List.of(alex, oswaldo, mike));
        for (Usuario u : List.of(alex, oswaldo, mike)) lenient().when(usuarioRepository.findById(u.getIdusuario())).thenReturn(java.util.Optional.of(u));
        lenient().when(comisionRepository.findAll()).thenReturn(List.of(
                comision(10, "10"), comision(11, "4"), comision(12, "4")));
        lenient().when(descuentoRepository.findByAnioAndMesOrderByIddescuentoAsc(2026, 10)).thenReturn(List.of());
        lenient().when(gastoRepository.findByActivoTrueAndRecurrenteFalseAndFechaBetweenOrderByFechaAscIdgastoAsc(any(), any())).thenReturn(List.of());
        lenient().when(gastoRepository.findByActivoTrueAndRecurrenteTrueAndFechaLessThanEqualOrderByFechaAscIdgastoAsc(any())).thenReturn(List.of());
        lenient().when(nominaDetalleRepository.findByEstadoAndFechaPagoBetweenOrderByFechaPagoAsc(any(), any(), any())).thenReturn(List.of());
    }

    private Usuario tecnico(int id, String nombre) {
        return Usuario.builder().idusuario(id).username(nombre.toLowerCase()).nombreCompleto(nombre).rol(Rol.TECNICO).activo(true).build();
    }

    private ComisionTallerTecnico comision(int id, String pct) {
        return ComisionTallerTecnico.builder().idusuario(id).porcentaje(new BigDecimal(pct)).build();
    }

    private Object[] linea(Usuario t, int orden, String precio, int cantidad, String costo) {
        return new Object[]{t.getIdusuario(), t.getNombreCompleto(), 1, "Corpo", orden, new BigDecimal(precio), (short) cantidad, new BigDecimal(costo)};
    }

    private List<Object[]> lineas(Object[]... filas) {
        return java.util.Arrays.asList(filas);
    }

    private Gasto gasto(String categoria, String monto, boolean recurrente, LocalDate fecha, LocalDate hasta) {
        return Gasto.builder().idgasto(1).fecha(fecha).concepto(categoria).categoria(categoria).monto(new BigDecimal(monto))
                .recurrente(recurrente).hastaFecha(hasta).activo(true).build();
    }

    @Test
    @DisplayName("octubre 2026: 92,250 de ventas − 29,296 de piezas − 15,400 de sueldos − 9,245 de gastos = 38,309 netos; Alex gana el 10 %")
    void libretaDeOctubre() {
        lenient().when(ordenRepository.lineasEntregadas(any(), any())).thenReturn(List.of(
                linea(alex, 1, "42000", 1, "13000"), linea(alex, 2, "13500", 1, "5296"),
                linea(mike, 3, "30000", 1, "11000"), linea(oswaldo, 4, "6750", 1, "0")));
        lenient().when(gastoRepository.findByActivoTrueAndRecurrenteFalseAndFechaBetweenOrderByFechaAscIdgastoAsc(any(), any())).thenReturn(List.of(
                gasto("SUELDOS", "15400", false, LocalDate.of(2026, 10, 31), null),
                gasto("GASOLINA", "695", false, LocalDate.of(2026, 10, 15), null),
                gasto("HERRAMIENTA", "2500", false, LocalDate.of(2026, 10, 20), null),
                gasto("SOFTWARE", "1050", false, LocalDate.of(2026, 10, 25), null)));
        lenient().when(gastoRepository.findByActivoTrueAndRecurrenteTrueAndFechaLessThanEqualOrderByFechaAscIdgastoAsc(any())).thenReturn(List.of(
                gasto("RENTA", "5000", true, LocalDate.of(2026, 1, 1), null)));

        Map<String, Object> r = service.mensual(2026, 10, "root");

        assertThat((BigDecimal) r.get("ventas")).isEqualByComparingTo("92250.00");
        assertThat((BigDecimal) r.get("inversion")).isEqualByComparingTo("29296.00");
        assertThat((BigDecimal) r.get("gananciaBruta")).isEqualByComparingTo("62954.00");
        assertThat((BigDecimal) r.get("gastosFijos")).isEqualByComparingTo("15400");
        assertThat((BigDecimal) r.get("gastosOperativos")).isEqualByComparingTo("9245.00");
        assertThat((BigDecimal) r.get("gananciaNeta")).isEqualByComparingTo("38309.00");

        @SuppressWarnings("unchecked") List<Map<String, Object>> tecnicos = (List<Map<String, Object>>) r.get("tecnicos");
        Map<String, Object> a = tecnicos.stream().filter(t -> t.get("tecnico").equals("Alex")).findFirst().orElseThrow();
        assertThat((BigDecimal) a.get("generado")).isEqualByComparingTo("37204.00");
        assertThat((BigDecimal) a.get("comision")).isEqualByComparingTo("3830.90");
        Map<String, Object> o = tecnicos.stream().filter(t -> t.get("tecnico").equals("Oswaldo")).findFirst().orElseThrow();
        assertThat((BigDecimal) o.get("comision")).isEqualByComparingTo("1532.36");
    }

    @Test
    @DisplayName("lo descontado a un técnico se resta de su comisión; la renta recurrente cuenta cada mes hasta su fecha final")
    void descuentosYRecurrentes() {
        lenient().when(ordenRepository.lineasEntregadas(any(), any())).thenReturn(lineas(linea(alex, 1, "10000", 1, "0")));
        lenient().when(descuentoRepository.findByAnioAndMesOrderByIddescuentoAsc(2026, 10)).thenReturn(List.of(
                ComisionTallerDescuento.builder().iddescuento(1).anio(2026).mes(10).idusuario(10).concepto("Pantalla pendiente").monto(new BigDecimal("535")).build(),
                ComisionTallerDescuento.builder().iddescuento(2).anio(2026).mes(10).idusuario(10).concepto("Batería iPhone").monto(new BigDecimal("550")).build()));
        lenient().when(gastoRepository.findByActivoTrueAndRecurrenteTrueAndFechaLessThanEqualOrderByFechaAscIdgastoAsc(any())).thenReturn(List.of(
                gasto("RENTA", "5000", true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30)),   // ya terminó en septiembre
                gasto("SERVICIOS", "1000", true, LocalDate.of(2026, 3, 5), null)));

        Map<String, Object> r = service.mensual(2026, 10, "root");

        assertThat((BigDecimal) r.get("gastosOperativos")).isEqualByComparingTo("1000");   // la renta ya no cuenta
        assertThat((BigDecimal) r.get("gananciaNeta")).isEqualByComparingTo("9000");
        @SuppressWarnings("unchecked") List<Map<String, Object>> tecnicos = (List<Map<String, Object>>) r.get("tecnicos");
        Map<String, Object> a = tecnicos.stream().filter(t -> t.get("tecnico").equals("Alex")).findFirst().orElseThrow();
        assertThat((BigDecimal) a.get("comision")).isEqualByComparingTo("900.00");
        assertThat((BigDecimal) a.get("totalDescuentos")).isEqualByComparingTo("1085.00");
        assertThat((BigDecimal) a.get("aPagar")).isEqualByComparingTo("-185.00");
    }

    @Test
    @DisplayName("si el mes termina en pérdida no hay comisión; el mes sin movimientos da ceros")
    void sinGanancia() {
        lenient().when(ordenRepository.lineasEntregadas(any(), any())).thenReturn(lineas(linea(alex, 1, "1000", 1, "0")));
        lenient().when(gastoRepository.findByActivoTrueAndRecurrenteFalseAndFechaBetweenOrderByFechaAscIdgastoAsc(any(), any())).thenReturn(List.of(
                gasto("RENTA", "5000", false, LocalDate.of(2026, 10, 1), null)));
        Map<String, Object> r = service.mensual(2026, 10, "root");
        assertThat((BigDecimal) r.get("gananciaNeta")).isNegative();
        @SuppressWarnings("unchecked") List<Map<String, Object>> tecnicos = (List<Map<String, Object>>) r.get("tecnicos");
        assertThat(tecnicos).allSatisfy(t -> assertThat((BigDecimal) t.get("comision")).isEqualByComparingTo("0"));

        lenient().when(ordenRepository.lineasEntregadas(any(), any())).thenReturn(lineas());
        assertThat((BigDecimal) service.mensual(2026, 9, "root").get("ventas")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("la nómina pagada desde el sistema cuenta solo la de los técnicos: la del personal de tiendas no es del taller")
    void nominaSoloDeTecnicos() {
        lenient().when(ordenRepository.lineasEntregadas(any(), any())).thenReturn(lineas(linea(alex, 1, "20000", 1, "0")));
        Usuario oscar = Usuario.builder().idusuario(30).username("oscar").nombreCompleto("Oscar").rol(Rol.VENDEDOR).activo(true).build();
        lenient().when(nominaDetalleRepository.findByEstadoAndFechaPagoBetweenOrderByFechaPagoAsc(any(), any(), any())).thenReturn(List.of(
                pago(alex, "8000"), pago(oscar, "6000")));

        Map<String, Object> r = service.mensual(2026, 10, "root");

        assertThat((BigDecimal) r.get("nominaSistema")).isEqualByComparingTo("8000");
        assertThat((BigDecimal) r.get("gastosFijos")).isEqualByComparingTo("8000");
        assertThat((BigDecimal) r.get("gananciaNeta")).isEqualByComparingTo("12000");
        @SuppressWarnings("unchecked") List<Map<String, Object>> filas = (List<Map<String, Object>>) r.get("nomina");
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).get("empleado")).isEqualTo("Alex");
    }

    private com.skycel.backend.domain.entity.NominaDetalle pago(Usuario u, String neto) {
        return com.skycel.backend.domain.entity.NominaDetalle.builder()
                .empleado(com.skycel.backend.domain.entity.EmpleadoPerfil.builder().usuario(u).build())
                .totalNeto(new BigDecimal(neto)).build();
    }

    @Test
    @DisplayName("solo la administración; categoría y mes válidos")
    void validaciones() {
        assertThatThrownBy(() -> service.mensual(2026, 10, "vendedor"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.mensual(2026, 13, "root"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        GastoRequestDto dto = new GastoRequestDto();
        dto.setFecha(LocalDate.of(2026, 10, 1)); dto.setConcepto("X"); dto.setCategoria("NO_EXISTE"); dto.setMonto(BigDecimal.TEN);
        assertThatThrownBy(() -> service.crearGasto(dto, "root"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
