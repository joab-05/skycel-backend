package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.PromocionEquipo;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.promocion.PromocionEquipoDto;
import com.skycel.backend.repository.CategoriaRepository;
import com.skycel.backend.repository.PromocionEquipoRepository;
import com.skycel.backend.service.PromocionEquipoService.Beneficio;
import com.skycel.backend.service.PromocionEquipoService.EntradaLinea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromocionEquipoServiceTest {

    private static final Short CHIP = 10, MICA = 20, PROTECTOR = 30, CABLE = 40;

    @Mock private PromocionEquipoRepository repository;
    @Mock private CategoriaRepository categoriaRepository;
    @InjectMocks private PromocionEquipoService service;

    private static PromocionEquipo promo() {
        return PromocionEquipo.builder().id(1).activo(true)
                .idCategoriaChip(CHIP).idCategoriaMica(MICA).idCategoriaProtector(PROTECTOR)
                .descuentoMica(new BigDecimal("50")).descuentoProtector(new BigDecimal("100"))
                .protectorPrecioMenorA(new BigDecimal("249"))
                .chipsPorEquipo(1).micasPorEquipo(1).protectoresPorEquipo(1).build();
    }

    private static EntradaLinea equipo() {
        return new EntradaLinea(TipoProducto.CELULAR, Set.of((short) 1), new BigDecimal("3000"), 1);
    }

    private static EntradaLinea acc(Short categoria, String precio, int cantidad) {
        return new EntradaLinea(TipoProducto.ACCESORIO, Set.of(categoria, (short) 99), new BigDecimal(precio), cantidad);
    }

    @Test
    @DisplayName("el chip va de regalo: se le descuenta todo su precio")
    void chipDeRegalo() {
        List<Beneficio> b = PromocionEquipoService.calcular(promo(), List.of(equipo(), acc(CHIP, "30", 1)));

        assertThat(b.get(1).piezas()).isEqualTo(1);
        assertThat(b.get(1).descuentoPorPieza()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("la mica de $50 va de regalo; otra mica más cara recibe ese mismo descuento")
    void micaDe50YOtraMica() {
        List<Beneficio> barata = PromocionEquipoService.calcular(promo(), List.of(equipo(), acc(MICA, "50", 1)));
        List<Beneficio> cara = PromocionEquipoService.calcular(promo(), List.of(equipo(), acc(MICA, "180", 1)));

        assertThat(barata.get(1).descuentoPorPieza()).isEqualByComparingTo("50");   // queda en $0
        assertThat(cara.get(1).descuentoPorPieza()).isEqualByComparingTo("50");     // queda en $130
    }

    @Test
    @DisplayName("el protector de menos de $249 se descuenta $100; uno de $249 o más no")
    void protector() {
        List<Beneficio> barato = PromocionEquipoService.calcular(promo(), List.of(equipo(), acc(PROTECTOR, "248", 1)));
        List<Beneficio> caro = PromocionEquipoService.calcular(promo(), List.of(equipo(), acc(PROTECTOR, "249", 1)));
        List<Beneficio> muyBarato = PromocionEquipoService.calcular(promo(), List.of(equipo(), acc(PROTECTOR, "80", 1)));

        assertThat(barato.get(1).descuentoPorPieza()).isEqualByComparingTo("100");
        assertThat(caro.get(1).piezas()).isZero();
        assertThat(muyBarato.get(1).descuentoPorPieza()).isEqualByComparingTo("80");   // nunca más que su precio
    }

    @Test
    @DisplayName("sin equipo en la venta no hay promoción")
    void sinEquipo() {
        List<Beneficio> b = PromocionEquipoService.calcular(promo(), List.of(acc(CHIP, "30", 1), acc(MICA, "50", 1)));

        assertThat(b).allSatisfy(x -> assertThat(x.piezas()).isZero());
    }

    @Test
    @DisplayName("apagada, no aplica")
    void apagada() {
        PromocionEquipo p = promo();
        p.setActivo(false);

        List<Beneficio> b = PromocionEquipoService.calcular(p, List.of(equipo(), acc(CHIP, "30", 1)));

        assertThat(b.get(1).piezas()).isZero();
    }

    @Test
    @DisplayName("una pieza de cada cosa por equipo: dos micas con un equipo, solo la primera recibe la promoción")
    void unaPorEquipo() {
        List<Beneficio> b = PromocionEquipoService.calcular(promo(), List.of(equipo(), acc(MICA, "50", 1), acc(MICA, "120", 1)));

        assertThat(b.get(1).piezas()).isEqualTo(1);
        assertThat(b.get(2).piezas()).isZero();
    }

    @Test
    @DisplayName("dos equipos dan derecho a dos piezas; un renglón de 3 recibe solo 2")
    void dosEquipos() {
        List<Beneficio> b = PromocionEquipoService.calcular(promo(), List.of(equipo(), equipo(), acc(CHIP, "30", 3)));

        assertThat(b.get(2).piezas()).isEqualTo(2);
    }

    @Test
    @DisplayName("el orden del carrito no importa: el accesorio puede ir antes del equipo")
    void accesorioAntesDelEquipo() {
        List<Beneficio> b = PromocionEquipoService.calcular(promo(), List.of(acc(CHIP, "30", 1), equipo()));

        assertThat(b.get(0).piezas()).isEqualTo(1);
    }

    @Test
    @DisplayName("la mica de una venta aparte del mismo carrito recibe la promoción por el equipo de la otra venta")
    void equipoEnOtraVenta() {
        List<Beneficio> b = PromocionEquipoService.calcular(promo(), List.of(acc(MICA, "99", 1)),
                new PromocionEquipoService.Previos(1, 0, 0, 0));

        assertThat(b.get(0).piezas()).isEqualTo(1);
        assertThat(b.get(0).tipo()).isEqualTo("MICA");
    }

    @Test
    @DisplayName("lo que otra venta del carrito ya recibió se descuenta del derecho de este equipo")
    void yaRecibidaEnOtraVenta() {
        List<Beneficio> b = PromocionEquipoService.calcular(promo(), List.of(acc(MICA, "99", 1)),
                new PromocionEquipoService.Previos(1, 0, 1, 0));

        assertThat(b.get(0).piezas()).isZero();
    }

    @Test
    @DisplayName("un artículo de otra categoría no recibe nada")
    void otraCategoria() {
        List<Beneficio> b = PromocionEquipoService.calcular(promo(), List.of(equipo(), acc(CABLE, "15", 1)));

        assertThat(b.get(1).piezas()).isZero();
    }

    @Test
    @DisplayName("la ruta de una categoría incluye a las que la contienen")
    void ruta() {
        Categoria principal = Categoria.builder().idcat((short) 1).nombre("Accesorios").build();
        Categoria sub = Categoria.builder().idcat((short) 2).nombre("Micas").categoriaSuperior(principal).build();
        Categoria hoja = Categoria.builder().idcat((short) 3).nombre("Hidrogel").categoriaSuperior(sub).build();

        assertThat(PromocionEquipoService.ruta(hoja)).containsExactlyInAnyOrder((short) 1, (short) 2, (short) 3);
        assertThat(PromocionEquipoService.ruta(null)).isEmpty();
    }

    @Test
    @DisplayName("la primera vez se crea con lo acordado y con las categorías que se llaman chip, mica y protector")
    void creaPorOmision() {
        Categoria accesorios = Categoria.builder().idcat((short) 1).nombre("Accesorios").build();
        Categoria micas = Categoria.builder().idcat((short) 5).nombre("Micas").categoriaSuperior(accesorios).build();
        Categoria chips = Categoria.builder().idcat((short) 6).nombre("Chips").build();
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(categoriaRepository.findByActivoTrue()).thenReturn(List.of(accesorios, micas, chips));
        when(repository.save(any(PromocionEquipo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoriaRepository.findById((short) 5)).thenReturn(Optional.of(micas));
        when(categoriaRepository.findById((short) 6)).thenReturn(Optional.of(chips));

        PromocionEquipoDto dto = service.obtener();

        assertThat(dto.getActivo()).isTrue();
        assertThat(dto.getIdCategoriaMica()).isEqualTo((short) 5);
        assertThat(dto.getIdCategoriaChip()).isEqualTo((short) 6);
        assertThat(dto.getIdCategoriaProtector()).isNull();
        assertThat(dto.getDescuentoMica()).isEqualByComparingTo("50");
        assertThat(dto.getDescuentoProtector()).isEqualByComparingTo("100");
        assertThat(dto.getProtectorPrecioMenorA()).isEqualByComparingTo("249");
        assertThat(dto.getCategoriaMica()).isEqualTo("Micas");
    }

    @Test
    @DisplayName("al guardar, una categoría que no existe se rechaza")
    void categoriaInexistente() {
        when(repository.findById(1)).thenReturn(Optional.of(promo()));
        when(categoriaRepository.existsById((short) 77)).thenReturn(false);
        PromocionEquipoDto dto = new PromocionEquipoDto();
        dto.setDescuentoMica(new BigDecimal("50"));
        dto.setDescuentoProtector(new BigDecimal("100"));
        dto.setProtectorPrecioMenorA(new BigDecimal("249"));
        dto.setChipsPorEquipo(1);
        dto.setMicasPorEquipo(1);
        dto.setProtectoresPorEquipo(1);
        dto.setIdCategoriaMica((short) 77);

        assertThatThrownBy(() -> service.actualizar(dto)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("no existe");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("al guardar, los montos no pueden ser negativos")
    void montoNegativo() {
        when(repository.findById(1)).thenReturn(Optional.of(promo()));
        PromocionEquipoDto dto = new PromocionEquipoDto();
        dto.setDescuentoMica(new BigDecimal("-1"));

        assertThatThrownBy(() -> service.actualizar(dto)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("descuento de la mica");
    }
}
