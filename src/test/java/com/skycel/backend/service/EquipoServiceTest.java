package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ProductoRequestDTO;
import com.skycel.backend.domain.dto.request.UnidadEquipoDTO;
import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.equipo.*;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Los equipos (celulares y tablets) se identifican por su IMEI o serie y apuntan directo a su artículo y sucursal.
 * Sin Spring ni base de datos: el repositorio de unidades se simula con un mapa en memoria.
 */
@ExtendWith(MockitoExtension.class)
class EquipoServiceTest {

    @Mock private ProductoImeiRepository imeiRepository;
    @Mock private ProductoMasterRepository masterRepository;
    @Mock private TiendaRepository tiendaRepository;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private SeccionRepository seccionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ProductoService productoService;
    @Mock private DescuentoService descuentoService;
    @Mock private MovimientoInventarioService movimientoInventarioService;
    @Mock private PermisoService permisoService;

    @InjectMocks
    private EquipoService service;

    private final Map<String, ProductoImei> unidades = new HashMap<>();
    private Tienda zocalo, corpo;
    private Usuario admin, vendedor;
    private ProductoMaster celular, tablet, accesorio;

    @BeforeEach
    void setUp() {
        zocalo = Tienda.builder().codti(2).nombre("Zocalo").build();
        corpo = Tienda.builder().codti(3).nombre("Corpo").build();
        admin = Usuario.builder().idusuario(1).username("admin").rol(Rol.ADMIN).tienda(zocalo).build();
        vendedor = Usuario.builder().idusuario(2).username("yamilet").rol(Rol.VENDEDOR).tienda(zocalo).build();
        celular = ProductoMaster.builder().idprodmaster(1).tipo(TipoProducto.CELULAR).nombreBase("Celular Samsung A16").build();
        tablet = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.TABLET).nombreBase("Tablet Samsung Tab A9").build();
        accesorio = ProductoMaster.builder().idprodmaster(3).tipo(TipoProducto.ACCESORIO).nombreBase("Funda").build();

        lenient().when(tiendaRepository.findById(2)).thenReturn(Optional.of(zocalo));
        lenient().when(tiendaRepository.findById(3)).thenReturn(Optional.of(corpo));
        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("yamilet")).thenReturn(Optional.of(vendedor));
        lenient().when(permisoService.tiene(any(), anyString())).thenReturn(true);
        lenient().when(descuentoService.calcularDescuento(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(imeiRepository.save(any(ProductoImei.class))).thenAnswer(inv -> {
            ProductoImei u = inv.getArgument(0);
            unidades.put(u.getImei(), u);
            return u;
        });
        lenient().when(imeiRepository.findByImei(anyString())).thenAnswer(inv -> Optional.ofNullable(unidades.get((String) inv.getArgument(0))));
        lenient().when(imeiRepository.existsByImei(anyString())).thenAnswer(inv -> unidades.containsKey((String) inv.getArgument(0)));
        lenient().when(masterRepository.findById(1)).thenReturn(Optional.of(celular));
        lenient().when(masterRepository.findById(2)).thenReturn(Optional.of(tablet));
        lenient().when(productoService.resolverMaster(any(ProductoRequestDTO.class))).thenAnswer(inv -> {
            ProductoRequestDTO d = inv.getArgument(0);
            if (d.getIdProductoMaster() == 2) return tablet;
            if (d.getIdProductoMaster() == 3) return accesorio;
            return celular;
        });
    }

    private UnidadEquipoDTO unidad(String imei, String condicion, String costo, String precio) {
        UnidadEquipoDTO u = new UnidadEquipoDTO();
        u.setImei(imei);
        u.setCondicion(condicion);
        u.setCostoUnitario(costo == null ? null : new BigDecimal(costo));
        u.setPrecioVenta(precio == null ? null : new BigDecimal(precio));
        return u;
    }

    private EquipoRequestDto alta(int idprodmaster, UnidadEquipoDTO... lista) {
        EquipoRequestDto dto = new EquipoRequestDto();
        dto.setCodti(2);
        dto.setIdProductoMaster(idprodmaster);
        dto.setUnidades(List.of(lista));
        dto.setCosto(new BigDecimal("2000"));
        dto.setPrecioVenta(new BigDecimal("3000"));
        return dto;
    }

    private ProductoImei existente(ProductoMaster m, Tienda t, String imei, String estado) {
        ProductoImei u = ProductoImei.builder().id((long) (unidades.size() + 1)).productoMaster(m).tienda(t).imei(imei)
                .estado(estado).condicion("NUEVO").precioVenta(new BigDecimal("3000")).costo(new BigDecimal("2000")).build();
        unidades.put(imei, u);
        return u;
    }

    private void assertEstado(HttpStatus esperado, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    // ── Alta ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("dar de alta equipos")
    class Alta {

        @Test
        @DisplayName("cada IMEI queda DISPONIBLE en su sucursal, apuntando a su artículo, con el precio y el costo; y deja historial")
        void celulares() {
            List<EquipoResponseDto> r = service.registrar(
                    alta(1, unidad("350000000000011", null, null, null), unidad("350000000000029", null, null, null)), "admin");

            assertThat(r).hasSize(2).extracting(EquipoResponseDto::getImei).containsExactly("350000000000011", "350000000000029");
            ProductoImei u = unidades.get("350000000000011");
            assertThat(u.getProductoMaster()).isSameAs(celular);
            assertThat(u.getTienda()).isSameAs(zocalo);
            assertThat(u.getEstado()).isEqualTo("DISPONIBLE");
            assertThat(u.getCondicion()).isEqualTo("NUEVO");
            assertThat(u.getTipoIdentificador()).isEqualTo("IMEI");
            assertThat(u.getPrecioVenta()).isEqualByComparingTo("3000");
            assertThat(u.getCosto()).isEqualByComparingTo("2000");
            verify(movimientoInventarioService, times(2)).registrarEquipo(any(ProductoImei.class), eq(+1), eq("ALTA"), anyString(), isNull());
        }

        @Test
        @DisplayName("un usado con su costo y su precio propios")
        void usadoConCostoYPrecio() {
            service.registrar(alta(1, unidad("350000000000011", "seminuevo", "800", "1500")), "admin");

            ProductoImei u = unidades.get("350000000000011");
            assertThat(u.getCondicion()).isEqualTo("SEMINUEVO");
            assertThat(u.getCosto()).isEqualByComparingTo("800");
            assertThat(u.getPrecioVenta()).isEqualByComparingTo("1500");
        }

        @Test
        @DisplayName("«usado» se sigue aceptando (así se llamaba) y queda como SEMINUEVO")
        void usadoEsSeminuevo() {
            service.registrar(alta(1, unidad("350000000000011", "usado", "800", "1500")), "admin");

            assertThat(unidades.get("350000000000011").getCondicion()).isEqualTo("SEMINUEVO");
        }

        @Test
        @DisplayName("un celular solo se registra por IMEI (15 dígitos); una tablet acepta IMEI o número de serie")
        void identificador() {
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.registrar(alta(1, unidad("R58M1234ABC", null, null, null)), "admin"));
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.registrar(alta(1, unidad("12345", null, null, null)), "admin"));

            service.registrar(alta(2, unidad("R58M1234ABC", null, null, null), unidad("350000000000037", null, null, null)), "admin");

            assertThat(unidades.get("R58M1234ABC").getTipoIdentificador()).isEqualTo("SERIE");
            assertThat(unidades.get("350000000000037").getTipoIdentificador()).isEqualTo("IMEI");
        }

        @Test
        @DisplayName("se quitan espacios y guiones y se pasa a mayúsculas")
        void normaliza() {
            service.registrar(alta(2, unidad(" r58m-1234 abc ", null, null, null)), "admin");

            assertThat(unidades).containsKey("R58M1234ABC");
        }

        @Test
        @DisplayName("IMEI repetido en la lista o ya registrado: 400 / 409, y no se guarda ninguno")
        void repetidos() {
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.registrar(
                    alta(1, unidad("350000000000011", null, null, null), unidad("350000000000011", null, null, null)), "admin"));
            existente(celular, corpo, "350000000000029", "VENDIDO");
            assertEstado(HttpStatus.CONFLICT, () -> service.registrar(
                    alta(1, unidad("350000000000011", null, null, null), unidad("350000000000029", null, null, null)), "admin"));

            assertThat(unidades).doesNotContainKey("350000000000011");
        }

        @Test
        @DisplayName("sin precio, con precio $0, con costo negativo o con condición inválida: 400")
        void datosInvalidos() {
            EquipoRequestDto sinPrecio = alta(1, unidad("350000000000011", null, null, null));
            sinPrecio.setPrecioVenta(null);
            assertThatThrownBy(() -> service.registrar(sinPrecio, "admin"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("precio");
            assertThatThrownBy(() -> service.registrar(alta(1, unidad("350000000000011", "seminuevo", "100", "0")), "admin"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("precio");
            assertThatThrownBy(() -> service.registrar(alta(1, unidad("350000000000011", "seminuevo", "-1", null)), "admin"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("costo");
            assertThatThrownBy(() -> service.registrar(alta(1, unidad("350000000000011", "roto", null, null)), "admin"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Condición inválida");
            assertThat(unidades).isEmpty();
        }

        @Test
        @DisplayName("un accesorio no es un equipo: 400; sin unidades: 400")
        void noEsEquipo() {
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.registrar(alta(3, unidad("350000000000011", null, null, null)), "admin"));
            EquipoRequestDto vacio = alta(1);
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.registrar(vacio, "admin"));
        }

        @Test
        @DisplayName("un vendedor solo da de alta en su sucursal; otra sucursal: 403")
        void otraSucursal() {
            EquipoRequestDto dto = alta(1, unidad("350000000000011", null, null, null));
            dto.setCodti(3);
            assertEstado(HttpStatus.FORBIDDEN, () -> service.registrar(dto, "yamilet"));

            dto.setCodti(2);
            assertThat(service.registrar(dto, "yamilet")).hasSize(1);
        }
    }

    // ── Corregir ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("corregir un equipo")
    class Editar {

        @Test
        @DisplayName("cambia precio, costo, condición y rezagado")
        void datos() {
            ProductoImei u = existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            EquipoUpdateDto dto = new EquipoUpdateDto();
            dto.setPrecioVenta(new BigDecimal("2500"));
            dto.setCosto(new BigDecimal("1800"));
            dto.setCondicion("reacondicionado");
            dto.setRezagado(true);

            EquipoResponseDto r = service.editar("350000000000011", dto, "admin");

            assertThat(u.getPrecioVenta()).isEqualByComparingTo("2500");
            assertThat(u.getCosto()).isEqualByComparingTo("1800");
            assertThat(u.getCondicion()).isEqualTo("REACONDICIONADO");
            assertThat(u.getRezagado()).isTrue();
            assertThat(r.getPrecioFinal()).isEqualByComparingTo("2500");
        }

        @Test
        @DisplayName("se puede corregir el IMEI y el artículo de una unidad disponible")
        void imeiYArticulo() {
            ProductoImei u = existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            EquipoUpdateDto dto = new EquipoUpdateDto();
            dto.setNuevoImei("350000000000029");
            dto.setIdProductoMaster(2);

            service.editar("350000000000011", dto, "admin");

            assertThat(u.getImei()).isEqualTo("350000000000029");
            assertThat(u.getProductoMaster()).isSameAs(tablet);
        }

        @Test
        @DisplayName("el IMEI nuevo ya registrado en otro equipo: 409")
        void imeiDuplicado() {
            existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            existente(celular, zocalo, "350000000000029", "DISPONIBLE");
            EquipoUpdateDto dto = new EquipoUpdateDto();
            dto.setNuevoImei("350000000000029");

            assertEstado(HttpStatus.CONFLICT, () -> service.editar("350000000000011", dto, "admin"));
        }

        @Test
        @DisplayName("una unidad vendida o en camino conserva su IMEI y su artículo (ya figuran en documentos): 409; sus otros datos sí se corrigen")
        void vendida() {
            ProductoImei u = existente(celular, zocalo, "350000000000011", "VENDIDO");
            EquipoUpdateDto dto = new EquipoUpdateDto();
            dto.setNuevoImei("350000000000029");
            assertEstado(HttpStatus.CONFLICT, () -> service.editar("350000000000011", dto, "admin"));

            EquipoUpdateDto soloPrecio = new EquipoUpdateDto();
            soloPrecio.setPrecioVenta(new BigDecimal("999"));
            service.editar("350000000000011", soloPrecio, "admin");
            assertThat(u.getPrecioVenta()).isEqualByComparingTo("999");
        }

        @Test
        @DisplayName("precio $0 o costo negativo: 400; IMEI inexistente: 404; sucursal ajena: 403")
        void errores() {
            existente(celular, corpo, "350000000000011", "DISPONIBLE");
            EquipoUpdateDto cero = new EquipoUpdateDto();
            cero.setPrecioVenta(BigDecimal.ZERO);
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.editar("350000000000011", cero, "admin"));
            assertEstado(HttpStatus.NOT_FOUND, () -> service.editar("999999999999999", new EquipoUpdateDto(), "admin"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.editar("350000000000011", new EquipoUpdateDto(), "yamilet"));
        }
    }

    // ── Baja, precio del artículo, consultas ─────────────────────────────────

    @Nested
    @DisplayName("baja, precio por artículo y consultas")
    class Otros {

        @Test
        @DisplayName("dar de baja un equipo disponible: DEBAJA, con motivo y con historial; uno vendido no")
        void baja() {
            ProductoImei u = existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            existente(celular, zocalo, "350000000000029", "VENDIDO");

            assertEstado(HttpStatus.BAD_REQUEST, () -> service.darDeBaja("350000000000011", " ", "admin"));
            service.darDeBaja("350000000000011", "Robo en vitrina", "admin");

            assertThat(u.getEstado()).isEqualTo("DEBAJA");
            verify(movimientoInventarioService).registrarEquipo(u, -1, "SALIDA", "Robo en vitrina", null);
            assertEstado(HttpStatus.CONFLICT, () -> service.darDeBaja("350000000000029", "Error", "admin"));
        }

        @Test
        @DisplayName("el mismo precio a todas las unidades disponibles de un artículo en una sucursal")
        void precioDelArticulo() {
            ProductoImei a = existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            ProductoImei b = existente(celular, zocalo, "350000000000029", "DISPONIBLE");
            when(imeiRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(1, 2, "DISPONIBLE")).thenReturn(List.of(a, b));
            PrecioArticuloDto dto = new PrecioArticuloDto();
            dto.setPrecioVenta(new BigDecimal("2700"));

            int n = service.precioDelArticulo(1, 2, dto, "admin");

            assertThat(n).isEqualTo(2);
            assertThat(a.getPrecioVenta()).isEqualByComparingTo("2700");
            assertThat(b.getPrecioVenta()).isEqualByComparingTo("2700");
            assertThat(a.getCosto()).isEqualByComparingTo("2000");   // el costo no se toca si no se indica
        }

        @Test
        @DisplayName("sin el permiso de ver costos, el equipo no muestra su costo")
        void costoOculto() {
            existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            when(permisoService.tiene(Rol.VENDEDOR, "COSTOS_VER")).thenReturn(false);
            when(permisoService.tiene(Rol.ADMIN, "COSTOS_VER")).thenReturn(true);

            assertThat(service.porImei("350000000000011", "yamilet").getCosto()).isNull();
            assertThat(service.porImei("350000000000011", "admin").getCosto()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("el descuento vigente se refleja en el precio final")
        void descuento() {
            existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            when(descuentoService.calcularDescuento(eq(celular), eq(2), eq(new BigDecimal("3000")), any())).thenReturn(new BigDecimal("300"));

            EquipoResponseDto r = service.porImei("350000000000011", "admin");

            assertThat(r.getDescuentoAplicado()).isEqualByComparingTo("300");
            assertThat(r.getPrecioFinal()).isEqualByComparingTo("2700");
        }

        @Test
        @DisplayName("los artículos de una sucursal cuentan solo las unidades disponibles; las que están en garantía se ven aparte")
        void articulos() {
            ProductoImei a = existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            ProductoImei b = existente(celular, zocalo, "350000000000029", "DISPONIBLE");
            ProductoImei c = existente(celular, zocalo, "350000000000037", "EN_GARANTIA");
            when(imeiRepository.deLaSucursal(eq(2), any())).thenReturn(List.of(a, b, c));
            when(masterRepository.findByTipoInAndStockMinimoEquiposGreaterThanAndActivoTrue(any(), eq(0))).thenReturn(new ArrayList<>());
            celular.setStockMinimoEquipos(2);

            List<EquipoArticuloDto> r = service.articulos(2, false, "admin");

            assertThat(r).hasSize(1);
            assertThat(r.get(0).getDisponibles()).isEqualTo(2);
            assertThat(r.get(0).getEnGarantia()).isEqualTo(1);
            assertThat(r.get(0).getBajoStock()).isTrue();   // 2 disponibles <= mínimo 2
        }

        @Test
        @DisplayName("buscar para vender: exige al menos 2 caracteres")
        void buscar() {
            assertThat(service.buscarDisponibles(2, "a", "admin")).isEmpty();
            ProductoImei u = existente(celular, zocalo, "350000000000011", "DISPONIBLE");
            when(imeiRepository.buscarDisponibles(2, "A16")).thenReturn(List.of(u));

            assertThat(service.buscarDisponibles(2, " A16 ", "admin")).hasSize(1);
            ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
            verify(imeiRepository).buscarDisponibles(eq(2), texto.capture());
            assertThat(texto.getValue()).isEqualTo("A16");
        }
    }
}
