package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoImei;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.repository.ProductoImeiRepository;
import com.skycel.backend.repository.ProductoMasterRepository;
import com.skycel.backend.repository.ProductoRepository;
import com.skycel.backend.repository.UsuarioRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrecioServiceTest {

    @Mock private ProductoMasterRepository masterRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private ProductoImeiRepository imeiRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PermisoService permisoService;
    @InjectMocks private PrecioService service;

    private ProductoMaster master(int id, String nombre, TipoProducto tipo) {
        ProductoMaster m = new ProductoMaster();
        m.setIdprodmaster(id);
        m.setNombreBase(nombre);
        m.setTipo(tipo);
        m.setActivo(true);
        Categoria c = new Categoria();
        c.setNombre("Fundas");
        m.setCategoria(c);
        return m;
    }

    private Producto producto(String precio) {
        Producto p = new Producto();
        p.setPreciopub(new BigDecimal(precio));
        return p;
    }

    @Test
    @DisplayName("un accesorio cambia de precio en todas las sucursales y la bodega")
    void cambiaElPrecioDeUnAccesorioEnTodasLasSucursales() {
        ProductoMaster funda = master(1, "Funda Samsung A15", TipoProducto.ACCESORIO);
        Producto zocalo = producto("149"), corpo = producto("159"), bodega = producto("139");
        when(masterRepository.findById(1)).thenReturn(Optional.of(funda));
        when(productoRepository.findByProductoMaster_IdprodmasterOrderByIdproductoAsc(1)).thenReturn(List.of(zocalo, corpo, bodega));

        PrecioService.Resultado r = service.aplicar(List.of(new PrecioService.Cambio(1, new BigDecimal("99"))), "root");

        assertThat(r.articulos()).isEqualTo(1);
        assertThat(r.registros()).isEqualTo(3);
        assertThat(List.of(zocalo, corpo, bodega)).allSatisfy(p -> assertThat(p.getPreciopub()).isEqualByComparingTo("99"));
    }

    @Test
    @DisplayName("un celular cambia el precio de todas sus unidades disponibles, en cualquier sucursal")
    void cambiaElPrecioDeLasUnidadesDeUnEquipo() {
        ProductoMaster a15 = master(2, "Galaxy A15", TipoProducto.CELULAR);
        ProductoImei u1 = new ProductoImei(), u2 = new ProductoImei();
        when(masterRepository.findById(2)).thenReturn(Optional.of(a15));
        when(imeiRepository.findByProductoMaster_IdprodmasterAndEstado(2, ProductoImei.DISPONIBLE)).thenReturn(List.of(u1, u2));

        PrecioService.Resultado r = service.aplicar(List.of(new PrecioService.Cambio(2, new BigDecimal("3299"))), "root");

        assertThat(r.registros()).isEqualTo(2);
        assertThat(u1.getPrecioVenta()).isEqualByComparingTo("3299");
        assertThat(u2.getPrecioVenta()).isEqualByComparingTo("3299");
    }

    @Test
    @DisplayName("un precio en cero o negativo no se acepta y no se toca nada")
    void rechazaPreciosInvalidos() {
        assertThatThrownBy(() -> service.aplicar(List.of(new PrecioService.Cambio(1, BigDecimal.ZERO)), "root"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.aplicar(List.of(), "root")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("la lista trae el precio más bajo y más alto por artículo, filtra por texto y solo muestra costos a quien puede verlos")
    void listaConPreciosYFiltro() {
        ProductoMaster funda = master(1, "Funda Samsung A15", TipoProducto.ACCESORIO);
        ProductoMaster mica = master(3, "Mica Vidrio", TipoProducto.ACCESORIO);
        mica.getCategoria().setNombre("Micas");
        when(masterRepository.findByActivoTrue()).thenReturn(List.of(mica, funda));
        when(productoRepository.resumenDePrecios()).thenReturn(java.util.Collections.singletonList(
                new Object[]{1, new BigDecimal("99"), new BigDecimal("149"), new BigDecimal("40"), 6L}));
        when(imeiRepository.resumenDePreciosEquipos()).thenReturn(List.of());
        when(permisoService.tiene("COSTOS_VER")).thenReturn(true);

        var todos = service.listar(null, null);
        var soloFundas = service.listar("funda", null);

        assertThat(todos).extracting(PrecioService.Articulo::nombre).containsExactly("Funda Samsung A15", "Mica Vidrio");
        assertThat(soloFundas).hasSize(1);
        assertThat(soloFundas.get(0).precioMinimo()).isEqualByComparingTo("99");
        assertThat(soloFundas.get(0).precioMaximo()).isEqualByComparingTo("149");
        assertThat(soloFundas.get(0).costoMaximo()).isEqualByComparingTo("40");
        assertThat(todos.get(1).precioMinimo()).isNull();   // la mica no tiene existencias en ninguna sucursal

        lenient().when(permisoService.tiene("COSTOS_VER")).thenReturn(false);
        assertThat(service.listar("funda", null).get(0).costoMaximo()).isNull();
    }
}
