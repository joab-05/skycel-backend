package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.dto.producto.ExistenciaTiendaDto;
import com.skycel.backend.repository.ProductoImeiRepository;
import com.skycel.backend.repository.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** La búsqueda de existencias entre sucursales: sin costos, sin la sucursal de quien busca y con todas las palabras. */
@ExtendWith(MockitoExtension.class)
class ExistenciaTiendasServiceTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private ProductoImeiRepository imeiRepository;

    @InjectMocks
    private ExistenciaTiendasService service;

    private static Tienda tienda(int codti, String nombre, boolean almacen) {
        Tienda t = new Tienda();
        t.setCodti(codti);
        t.setNombre(nombre);
        t.setEsAlmacen(almacen);
        return t;
    }

    private static Producto accesorio(Tienda t, String codpro, String nombre, String stock, String precio) {
        ProductoMaster m = new ProductoMaster();
        m.setIdprodmaster(7);
        m.setNombreBase(nombre);
        Producto p = new Producto();
        p.setCodpro(codpro);
        p.setTienda(t);
        p.setProductoMaster(m);
        p.setStock(new BigDecimal(stock));
        p.setPreciopub(new BigDecimal(precio));
        p.setPreciopro(new BigDecimal("1"));
        return p;
    }

    @Test
    @DisplayName("menos de 2 letras no busca nada")
    void textoCorto() {
        assertThat(service.buscar("a", null)).isEmpty();
        assertThat(service.buscar("  ", null)).isEmpty();
        assertThat(service.buscar(null, null)).isEmpty();
    }

    @Test
    @DisplayName("lista accesorios y equipos de las otras sucursales, sin la de quien busca, ordenados por nombre y tienda")
    void otrasSucursales() {
        Tienda zocalo = tienda(2, "Zócalo", false), corpo = tienda(3, "Corpo", false), bodega = tienda(1, "Almacén Central", true);
        when(productoRepository.existenciasPorTexto(eq("funda"), any())).thenReturn(List.of(
                accesorio(zocalo, "FUN-000001", "Funda iPhone 13", "4", "120"),
                accesorio(corpo, "FUN-000001", "Funda iPhone 13", "2", "120"),
                accesorio(bodega, "FUN-000001", "Funda iPhone 13", "30", "120")));
        when(imeiRepository.existenciasEquipos("funda")).thenReturn(Collections.emptyList());

        List<ExistenciaTiendaDto> r = service.buscar("Funda", 2);

        assertThat(r).extracting(ExistenciaTiendaDto::tienda).containsExactly("Almacén Central", "Corpo");
        assertThat(r.get(0).esAlmacen()).isTrue();
        assertThat(r.get(0).existencia()).isEqualByComparingTo("30");
        assertThat(r.get(1).codpro()).isEqualTo("FUN-000001");
        assertThat(r.get(1).esEquipo()).isFalse();
    }

    @Test
    @DisplayName("varias palabras: busca por la más larga y exige que estén todas")
    void variasPalabras() {
        Tienda corpo = tienda(3, "Corpo", false);
        when(productoRepository.existenciasPorTexto(eq("iphone"), any())).thenReturn(List.of(
                accesorio(corpo, "FUN-000001", "Funda iPhone 13", "2", "120"),
                accesorio(corpo, "CAR-000002", "Cargador iPhone", "5", "200")));
        when(imeiRepository.existenciasEquipos("iphone")).thenReturn(Collections.emptyList());

        List<ExistenciaTiendaDto> r = service.buscar("funda iphone", null);

        assertThat(r).extracting(ExistenciaTiendaDto::codpro).containsExactly("FUN-000001");
    }

    @Test
    @DisplayName("los equipos salen por artículo con las unidades disponibles de cada sucursal")
    void equipos() {
        List<Object[]> filas = new ArrayList<>();
        filas.add(new Object[]{12, "Samsung A16 4/128gb Verde", 3, "Corpo", Boolean.FALSE, 2L, new BigDecimal("3499")});
        filas.add(new Object[]{12, "Samsung A16 4/128gb Verde", 2, "Zócalo", Boolean.FALSE, 1L, new BigDecimal("3599")});
        when(productoRepository.existenciasPorTexto(eq("a16"), any())).thenReturn(List.of());
        when(imeiRepository.existenciasEquipos("a16")).thenReturn(filas);

        List<ExistenciaTiendaDto> r = service.buscar("a16", 2);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).esEquipo()).isTrue();
        assertThat(r.get(0).codpro()).isNull();
        assertThat(r.get(0).idprodmaster()).isEqualTo(12);
        assertThat(r.get(0).existencia()).isEqualByComparingTo("2");
        assertThat(r.get(0).precio()).isEqualByComparingTo("3499");
    }
}
