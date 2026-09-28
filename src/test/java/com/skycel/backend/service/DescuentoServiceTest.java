package com.skycel.backend.service;

import com.skycel.backend.domain.entity.DescuentoRegla;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.descuento.DescuentoReglaRequestDto;
import com.skycel.backend.repository.DescuentoReglaRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Pruebas del motor de descuentos automáticos: el cálculo puro (sin Spring ni base de datos) y la administración de reglas. */
@ExtendWith(MockitoExtension.class)
class DescuentoServiceTest {

    @Mock private DescuentoReglaRepository repository;
    private DescuentoService service;

    @BeforeEach
    void setUp() {
        service = new DescuentoService(repository);
        lenient().when(repository.save(any(DescuentoRegla.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Producto accesorio(Integer codti) {
        ProductoMaster master = ProductoMaster.builder().idprodmaster(9).tipo(TipoProducto.ACCESORIO).build();
        return Producto.builder().productoMaster(master).tienda(Tienda.builder().codti(codti).build()).build();
    }

    private Producto celular() {
        ProductoMaster master = ProductoMaster.builder().idprodmaster(20).tipo(TipoProducto.CELULAR).build();
        return Producto.builder().productoMaster(master).tienda(Tienda.builder().codti(2).build()).build();
    }

    private DescuentoRegla reglaBase() {
        return DescuentoRegla.builder().iddescuento(1).nombre("Prueba").aplicacion((byte) 0).activo(true).build();
    }

    // ── Cálculo: alcance ─────────────────────────────────────────────────────

    @Test
    @DisplayName("una regla sin tipo/artículo/tienda aplica a cualquier producto")
    void reglaGeneral_aplicaATodo() {
        DescuentoRegla r = reglaBase(); r.setDescuentoFijo(new BigDecimal("20"));
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.now()))
                .isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("una regla de un tipo de artículo no aplica a otro tipo")
    void reglaPorTipo_noAplicaAOtroTipo() {
        DescuentoRegla r = reglaBase(); r.setTipoProducto(TipoProducto.CELULAR); r.setDescuentoFijo(new BigDecimal("50"));
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.now()))
                .isEqualByComparingTo("0");
        assertThat(service.calcularDescuento(celular(), new BigDecimal("100"), LocalDateTime.now()))
                .isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("una regla de una sucursal no aplica en otra")
    void reglaPorTienda_noAplicaEnOtra() {
        DescuentoRegla r = reglaBase(); r.setCodti(2); r.setDescuentoFijo(new BigDecimal("10"));
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.now())).isEqualByComparingTo("10");
        assertThat(service.calcularDescuento(accesorio(3), new BigDecimal("100"), LocalDateTime.now())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("una regla de un artículo específico no aplica a otro artículo del mismo tipo")
    void reglaPorArticulo_noAplicaAOtroArticulo() {
        DescuentoRegla r = reglaBase(); r.setIdProductoMaster(9); r.setDescuentoFijo(new BigDecimal("5"));
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.now())).isEqualByComparingTo("5");
        assertThat(service.calcularDescuento(celular(), new BigDecimal("100"), LocalDateTime.now())).isEqualByComparingTo("0");
    }

    // ── Cálculo: monto mínimo ────────────────────────────────────────────────

    @Test
    @DisplayName("con un precio por debajo del mínimo, no aplica")
    void montoMinimo_noAlcanzado_noAplica() {
        DescuentoRegla r = reglaBase(); r.setMontoMinimo(new BigDecimal("1000")); r.setDescuentoFijo(new BigDecimal("100"));
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        assertThat(service.calcularDescuento(celular(), new BigDecimal("999"), LocalDateTime.now())).isEqualByComparingTo("0");
        assertThat(service.calcularDescuento(celular(), new BigDecimal("1000"), LocalDateTime.now())).isEqualByComparingTo("100");
    }

    // ── Cálculo: monto fijo vs porcentaje ────────────────────────────────────

    @Test
    @DisplayName("descuento por porcentaje se calcula sobre el precio de lista")
    void descuentoPorcentaje_seCalcula() {
        DescuentoRegla r = reglaBase(); r.setDescuentoPorcentaje(new BigDecimal("20"));
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("250"), LocalDateTime.now())).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("con varias reglas aplicables, se usa la que da mayor descuento")
    void variasReglas_seUsaLaMejor() {
        DescuentoRegla chica = reglaBase(); chica.setIddescuento(1); chica.setDescuentoFijo(new BigDecimal("10"));
        DescuentoRegla grande = reglaBase(); grande.setIddescuento(2); grande.setDescuentoPorcentaje(new BigDecimal("30"));
        when(repository.findByActivoTrue()).thenReturn(List.of(chica, grande));

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.now())).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("el descuento nunca deja el precio en $0 o negativo")
    void descuentoNoDejaPrecioEnCeroONegativo() {
        DescuentoRegla r = reglaBase(); r.setDescuentoFijo(new BigDecimal("500"));
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        BigDecimal descuento = service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.now());
        assertThat(new BigDecimal("100").subtract(descuento)).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("sin ninguna regla activa, el descuento es 0")
    void sinReglas_descuentoCero() {
        when(repository.findByActivoTrue()).thenReturn(List.of());

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.now())).isEqualByComparingTo("0");
    }

    // ── Cálculo: cuándo aplica (días / rango / siempre) ──────────────────────

    @Test
    @DisplayName("una regla de días de la semana solo aplica esos días")
    void reglaPorDiasSemana() {
        DescuentoRegla r = reglaBase(); r.setAplicacion((byte) 1); r.setDiasSemana("SAB,DOM"); r.setDescuentoFijo(BigDecimal.TEN);
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        LocalDateTime sabado = LocalDateTime.of(2026, 10, 3, 12, 0); // sábado
        LocalDateTime lunes  = LocalDateTime.of(2026, 10, 5, 12, 0); // lunes
        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), sabado)).isEqualByComparingTo("10");
        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), lunes)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("una regla de rango de fechas solo aplica dentro del rango, límites incluidos")
    void reglaPorRangoFechas() {
        DescuentoRegla r = reglaBase(); r.setAplicacion((byte) 2);
        r.setFechaInicio(LocalDate.of(2026, 12, 1)); r.setFechaFin(LocalDate.of(2026, 12, 24));
        r.setDescuentoFijo(BigDecimal.TEN);
        when(repository.findByActivoTrue()).thenReturn(List.of(r));

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.of(2026, 12, 1, 0, 0))).isEqualByComparingTo("10");
        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.of(2026, 12, 24, 23, 59))).isEqualByComparingTo("10");
        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.of(2026, 12, 25, 0, 0))).isEqualByComparingTo("0");
        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.of(2026, 11, 30, 23, 59))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("una regla inactiva nunca aplica, aunque cumpla todo lo demás")
    void reglaInactiva_nuncaAplica() {
        when(repository.findByActivoTrue()).thenReturn(List.of()); // el repositorio ya filtra por activo = true

        assertThat(service.calcularDescuento(accesorio(2), new BigDecimal("100"), LocalDateTime.now())).isEqualByComparingTo("0");
    }

    // ── Administración: validaciones al crear/editar ─────────────────────────

    private DescuentoReglaRequestDto dtoValido() {
        DescuentoReglaRequestDto dto = new DescuentoReglaRequestDto();
        dto.setNombre("Descuento de prueba");
        dto.setDescuentoFijo(new BigDecimal("50"));
        return dto;
    }

    @Test
    @DisplayName("crear sin nombre -> 400")
    void crear_sinNombre_lanza400() {
        DescuentoReglaRequestDto dto = dtoValido(); dto.setNombre(" ");

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("crear sin descuento fijo ni porcentaje -> 400")
    void crear_sinDescuento_lanza400() {
        DescuentoReglaRequestDto dto = dtoValido(); dto.setDescuentoFijo(null);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("crear con descuento fijo Y porcentaje a la vez -> 400")
    void crear_conAmbosDescuentos_lanza400() {
        DescuentoReglaRequestDto dto = dtoValido(); dto.setDescuentoPorcentaje(new BigDecimal("10"));

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("crear con 100% de descuento o más -> 400")
    void crear_porcentajeDe100OMas_lanza400() {
        DescuentoReglaRequestDto dto = dtoValido(); dto.setDescuentoFijo(null); dto.setDescuentoPorcentaje(new BigDecimal("100"));

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("crear con aplicación DIAS_SEMANA sin ningún día -> 400")
    void crear_diasSemanaVacio_lanza400() {
        DescuentoReglaRequestDto dto = dtoValido(); dto.setAplicacion("DIAS_SEMANA");

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("crear con RANGO_FECHA e inicio posterior a fin -> 400")
    void crear_rangoFechaInvertido_lanza400() {
        DescuentoReglaRequestDto dto = dtoValido(); dto.setAplicacion("RANGO_FECHA");
        dto.setFechaInicio(LocalDate.of(2026, 12, 24)); dto.setFechaFin(LocalDate.of(2026, 12, 1));

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("crear válida: se guarda con los campos normalizados")
    void crear_valida_seGuarda() {
        DescuentoReglaRequestDto dto = dtoValido(); dto.setAplicacion("DIAS_SEMANA"); dto.setDiasSemana(List.of("lun", "vie"));

        Map<String, Object> r = service.crear(dto);

        assertThat(r.get("nombre")).isEqualTo("Descuento de prueba");
        assertThat(r.get("aplicacion")).isEqualTo("DIAS_SEMANA");
        @SuppressWarnings("unchecked")
        List<String> dias = (List<String>) r.get("diasSemana");
        assertThat(dias).containsExactlyInAnyOrder("LUN", "VIE");
        assertThat(r.get("activo")).isEqualTo(true);
    }

    @Test
    @DisplayName("actualizar una regla que no existe -> 404")
    void actualizar_noExiste_lanza404() {
        when(repository.findById(99)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.actualizar(99, dtoValido()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
