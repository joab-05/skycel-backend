package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.traspaso.*;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pruebas de TraspasoService sin Spring ni base de datos. Los repositorios se simulan con una
 * "base de datos" en memoria, para recorrer el ciclo completo (despachar, recibir, anular).
 */
@ExtendWith(MockitoExtension.class)
class TraspasoServiceTest {

    @Mock private TraspasoRepository        traspasoRepository;
    @Mock private TraspasoDetalleRepository detalleRepository;
    @Mock private TiendaRepository          tiendaRepository;
    @Mock private UsuarioRepository         usuarioRepository;
    @Mock private ProductoRepository        productoRepository;
    @Mock private ProductoImeiRepository    productoImeiRepository;
    @Mock private ProductoService           productoService;

    @InjectMocks
    private TraspasoService service;

    // "base de datos" en memoria
    private final Map<Integer, Traspaso> traspasos = new HashMap<>();
    private final List<TraspasoDetalle> detalles = new ArrayList<>();
    private final List<Producto> productos = new ArrayList<>();
    private final Map<String, ProductoImei> imeis = new HashMap<>();
    private int siguienteId = 100;

    private Tienda almacen, zocalo, corpo;
    private Usuario root, encZocalo, encCorpo, vendedor;
    private ProductoMaster celular, accesorio, servicio;
    private Color negro, azul;

    @BeforeEach
    void setUp() {
        almacen = Tienda.builder().codti(1).nombre("Almacen").build();
        zocalo  = Tienda.builder().codti(2).nombre("Zocalo").build();
        corpo   = Tienda.builder().codti(3).nombre("Corpo").build();
        root       = usuario(1, "root", Rol.ROOT, almacen);
        encZocalo  = usuario(4, "abigail", Rol.ENCARGADO_TIENDA, zocalo);
        encCorpo   = usuario(5, "guillermo", Rol.ENCARGADO_TIENDA, corpo);
        vendedor   = usuario(9, "yamilet", Rol.VENDEDOR, zocalo);
        celular   = ProductoMaster.builder().idprodmaster(1).tipo(TipoProducto.CELULAR).nombreBase("Samsung Galaxy A15").build();
        accesorio = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.ACCESORIO).nombreBase("Funda Silicon").build();
        servicio  = ProductoMaster.builder().idprodmaster(3).tipo(TipoProducto.SERVICIO).nombreBase("Cambio de Pantalla").build();
        negro = Color.builder().idcolor((short) 1).nombre("Negro").build();
        azul  = Color.builder().idcolor((short) 3).nombre("Azul").build();

        for (Tienda t : List.of(almacen, zocalo, corpo)) lenient().when(tiendaRepository.findById(t.getCodti())).thenReturn(Optional.of(t));
        lenient().when(tiendaRepository.findById(99)).thenReturn(Optional.empty());
        for (Usuario u : List.of(root, encZocalo, encCorpo, vendedor)) lenient().when(usuarioRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));

        lenient().when(traspasoRepository.save(any(Traspaso.class))).thenAnswer(inv -> {
            Traspaso t = inv.getArgument(0);
            if (t.getIdtraspaso() == null) t.setIdtraspaso(siguienteId++);
            traspasos.put(t.getIdtraspaso(), t);
            return t;
        });
        lenient().when(traspasoRepository.findById(anyInt())).thenAnswer(inv -> Optional.ofNullable(traspasos.get((Integer) inv.getArgument(0))));
        lenient().when(detalleRepository.save(any(TraspasoDetalle.class))).thenAnswer(inv -> {
            detalles.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(detalleRepository.findByTraspaso_IdtraspasoOrderByIddetalle(anyInt())).thenAnswer(inv ->
                detalles.stream().filter(d -> d.getTraspaso().getIdtraspaso().equals(inv.getArgument(0))).collect(Collectors.toList()));

        lenient().when(productoRepository.findByCodproAndTienda_Codti(anyString(), anyInt())).thenAnswer(inv ->
                productos.stream().filter(p -> p.getCodpro().equals(inv.getArgument(0)) && p.getTienda().getCodti().equals(inv.getArgument(1))).findFirst());
        lenient().when(productoRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(anyInt(), anyInt())).thenAnswer(inv ->
                productos.stream().filter(p -> p.getProductoMaster().getIdprodmaster().equals(inv.getArgument(0))
                        && p.getTienda().getCodti().equals(inv.getArgument(1)) && Boolean.TRUE.equals(p.getActivo())).collect(Collectors.toList()));
        lenient().when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            if (!productos.contains(p)) {
                if (p.getIdproducto() == null) p.setIdproducto(siguienteId++);
                productos.add(p);
            }
            return p;
        });
        lenient().when(productoImeiRepository.findByImei(anyString())).thenAnswer(inv -> Optional.ofNullable(imeis.get((String) inv.getArgument(0))));
        lenient().when(productoImeiRepository.save(any(ProductoImei.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productoService.generarCodigoPara(any(ProductoMaster.class))).thenAnswer(inv ->
                ((ProductoMaster) inv.getArgument(0)).getTipo() == TipoProducto.CELULAR ? "CEL-000099" : "ACC-000099");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Usuario usuario(int id, String username, Rol rol, Tienda t) {
        return Usuario.builder().idusuario(id).username(username).nombreCompleto(username.toUpperCase()).rol(rol).tienda(t).build();
    }

    private Producto stock(String codpro, Tienda t, ProductoMaster m, String cantidad, Color color) {
        Producto p = Producto.builder().idproducto(siguienteId++).codpro(codpro).tienda(t).productoMaster(m)
                .stock(new BigDecimal(cantidad)).color(color).preciopro(new BigDecimal("100")).preciopub(new BigDecimal("150"))
                .activo(true).build();
        productos.add(p);
        return p;
    }

    private ProductoImei unidad(Producto p, String imei) {
        ProductoImei pi = ProductoImei.builder().id((long) siguienteId++).producto(p).imei(imei).estado("DISPONIBLE").build();
        imeis.put(imei, pi);
        return pi;
    }

    private TraspasoLineaRequestDto linea(String codpro, String cantidad, String... imeisViajan) {
        TraspasoLineaRequestDto l = new TraspasoLineaRequestDto();
        l.setCodpro(codpro);
        l.setCantidad(new BigDecimal(cantidad));
        l.setImeis(imeisViajan.length == 0 ? null : List.of(imeisViajan));
        return l;
    }

    private EnvioRequestDto envio(Integer origen, int destino, TraspasoLineaRequestDto... lineas) {
        EnvioRequestDto dto = new EnvioRequestDto();
        dto.setCodtiOrigen(origen);
        dto.setCodtiDestino(destino);
        dto.setLineas(List.of(lineas));
        return dto;
    }

    private SolicitudRequestDto solicitud(Integer solicitante, int surte, TraspasoLineaRequestDto... lineas) {
        SolicitudRequestDto dto = new SolicitudRequestDto();
        dto.setCodtiOrigen(solicitante);
        dto.setCodtiDestino(surte);
        dto.setLineas(List.of(lineas));
        return dto;
    }

    private Producto enTienda(String codpro, Tienda t) {
        return productos.stream().filter(p -> p.getCodpro().equals(codpro) && p.getTienda().equals(t)).findFirst().orElseThrow();
    }

    private void assertEstado(HttpStatus esperado, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    // ── Envío ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("crear un envío")
    class CrearEnvio {

        @Test
        @DisplayName("accesorio: el stock sale del origen y el envío queda Enviado, con su detalle")
        void accesorio() {
            Producto funda = stock("ACC-000001", almacen, accesorio, "40", negro);

            TraspasoResponseDto r = service.crearEnvio(envio(null, 2, linea("ACC-000001", "15")), "root");

            assertThat(funda.getStock()).isEqualByComparingTo("25");
            assertThat(r.getTipo()).isEqualTo((byte) 1);
            assertThat(r.getEstado()).isEqualTo((byte) 1);
            assertThat(r.getEstadoDisplay()).isEqualTo("Enviado");
            assertThat(r.getCodtiOrigen()).isEqualTo(1);
            assertThat(r.getCodtiDestino()).isEqualTo(2);
            assertThat(r.getLineas()).hasSize(1);
            assertThat(r.getLineas().get(0).getCantidad()).isEqualByComparingTo("15");
            assertThat(r.getLineas().get(0).getNombreProducto()).isEqualTo("Funda Silicon");
            assertThat(r.getLineas().get(0).getImeis()).isNull();
        }

        @Test
        @DisplayName("equipos: cada unidad queda TRASPASADO (en tránsito), el stock baja y el detalle lleva sus IMEI")
        void equipos() {
            Producto a15 = stock("CEL-000004", almacen, celular, "3", negro);
            ProductoImei u1 = unidad(a15, "350000000000011");
            ProductoImei u2 = unidad(a15, "350000000000029");
            unidad(a15, "350000000000037");

            TraspasoResponseDto r = service.crearEnvio(
                    envio(1, 2, linea("CEL-000004", "2", "350000000000011", "350000000000029")), "root");

            assertThat(u1.getEstado()).isEqualTo("TRASPASADO");
            assertThat(u2.getEstado()).isEqualTo("TRASPASADO");
            assertThat(imeis.get("350000000000037").getEstado()).isEqualTo("DISPONIBLE");
            assertThat(a15.getStock()).isEqualByComparingTo("1");
            assertThat(r.getLineas().get(0).getCantidad()).isEqualByComparingTo("2");
            assertThat(r.getLineas().get(0).getImeis()).containsExactly("350000000000011", "350000000000029");
            assertThat(detalles).hasSize(2).allSatisfy(d -> assertThat(d.getCantidad()).isEqualByComparingTo("1"));
        }

        @Test
        @DisplayName("un encargado envía desde su propia tienda si no indica el origen")
        void origenPorDefecto() {
            stock("ACC-000001", zocalo, accesorio, "10", negro);

            assertThat(service.crearEnvio(envio(null, 3, linea("ACC-000001", "2")), "abigail").getCodtiOrigen()).isEqualTo(2);
        }

        @Test
        @DisplayName("un encargado no puede enviar desde la tienda de otro (403) ni un vendedor enviar nada (403)")
        void permisos() {
            stock("ACC-000001", zocalo, accesorio, "10", negro);

            assertEstado(HttpStatus.FORBIDDEN, () -> service.crearEnvio(envio(2, 3, linea("ACC-000001", "1")), "guillermo"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.crearEnvio(envio(2, 3, linea("ACC-000001", "1")), "yamilet"));
        }

        @Test
        @DisplayName("stock insuficiente: 400")
        void stockInsuficiente() {
            stock("ACC-000001", almacen, accesorio, "5", negro);

            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("ACC-000001", "6")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Stock insuficiente");
        }

        @Test
        @DisplayName("origen y destino iguales, tienda inexistente o producto que no existe: 400")
        void datosInvalidos() {
            stock("ACC-000001", almacen, accesorio, "5", negro);

            assertThatThrownBy(() -> service.crearEnvio(envio(1, 1, linea("ACC-000001", "1")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no pueden ser la misma");
            assertThatThrownBy(() -> service.crearEnvio(envio(1, 99, linea("ACC-000001", "1")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Tienda no encontrada");
            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("NO-EXISTE", "1")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no existe");
        }

        @Test
        @DisplayName("un servicio no se puede traspasar: 400")
        void servicio() {
            stock("SRV-000001", almacen, servicio, "0", null);

            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("SRV-000001", "1")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("servicio");
        }

        @Test
        @DisplayName("equipos: cantidad distinta a los IMEI, IMEI repetido, de otro producto o no disponible: 400")
        void imeisInvalidos() {
            Producto a15 = stock("CEL-000004", almacen, celular, "3", negro);
            Producto otro = stock("CEL-000005", almacen, celular, "1", azul);
            unidad(a15, "350000000000011");
            unidad(a15, "350000000000029");
            unidad(otro, "350000000000037");
            ProductoImei vendida = unidad(a15, "350000000000045");
            vendida.setEstado("VENDIDO");

            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("CEL-000004", "2", "350000000000011")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("exactamente 2 IMEI");
            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("CEL-000004", "2", "350000000000011", "350000000000011")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("repetido");
            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("CEL-000004", "1", "350000000000037")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no pertenece");
            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("CEL-000004", "1", "350000000000045")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no está disponible");
            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("CEL-000004", "1", "999999999999999")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no está registrado");
        }

        @Test
        @DisplayName("un accesorio con IMEI, o una cantidad no entera de equipos: 400")
        void imeiEnAccesorioYCantidadNoEntera() {
            Producto a15 = stock("CEL-000004", almacen, celular, "3", negro);
            unidad(a15, "350000000000011");
            stock("ACC-000001", almacen, accesorio, "5", negro);

            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("ACC-000001", "1", "350000000000011")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no lleva IMEI");
            assertThatThrownBy(() -> service.crearEnvio(envio(1, 2, linea("CEL-000004", "1.5", "350000000000011")), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("número entero");
        }
    }

    // ── Recibir ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recibir un envío")
    class Recibir {

        @Test
        @DisplayName("accesorio: si la tienda destino no lo tenía, se crea su producto con los datos del origen y el stock llega")
        void creaElProductoEnDestino() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = service.crearEnvio(envio(1, 2, linea("ACC-000001", "15")), "root").getIdtraspaso();

            TraspasoResponseDto r = service.recibir(id, "abigail");

            Producto nuevo = enTienda("ACC-000099", zocalo);
            assertThat(nuevo.getStock()).isEqualByComparingTo("15");
            assertThat(nuevo.getPreciopub()).isEqualByComparingTo("150");
            assertThat(nuevo.getColor()).isSameAs(negro);
            assertThat(nuevo.getActivo()).isTrue();
            assertThat(r.getEstado()).isEqualTo((byte) 2);
            assertThat(r.getEstadoDisplay()).isEqualTo("Recibido");
            assertThat(r.getNombreValida()).isEqualTo("ABIGAIL");
        }

        @Test
        @DisplayName("si la tienda destino ya tiene ese producto (mismo maestro y color), le suma el stock")
        void sumaAlProductoExistente() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            Producto yaTenia = stock("ACC-000050", zocalo, accesorio, "5", negro);
            int id = service.crearEnvio(envio(1, 2, linea("ACC-000001", "15")), "root").getIdtraspaso();

            service.recibir(id, "abigail");

            assertThat(yaTenia.getStock()).isEqualByComparingTo("20");
            assertThat(productos).noneMatch(p -> p.getCodpro().equals("ACC-000099"));
        }

        @Test
        @DisplayName("un producto de otro color en la destino no se mezcla: se crea el suyo")
        void otroColorNoSeMezcla() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            Producto azulDestino = stock("ACC-000050", zocalo, accesorio, "5", azul);
            int id = service.crearEnvio(envio(1, 2, linea("ACC-000001", "15")), "root").getIdtraspaso();

            service.recibir(id, "abigail");

            assertThat(azulDestino.getStock()).isEqualByComparingTo("5");
            assertThat(enTienda("ACC-000099", zocalo).getStock()).isEqualByComparingTo("15");
        }

        @Test
        @DisplayName("equipos: cada IMEI pasa al producto de la destino, DISPONIBLE, conservando su condición y costo")
        void equipos() {
            Producto a15 = stock("CEL-000004", almacen, celular, "2", negro);
            ProductoImei usado = unidad(a15, "350000000000011");
            usado.setCondicion("USADO");
            usado.setCostoUnitario(new BigDecimal("1900"));
            unidad(a15, "350000000000029");
            int id = service.crearEnvio(envio(1, 2, linea("CEL-000004", "2", "350000000000011", "350000000000029")), "root").getIdtraspaso();
            assertThat(usado.getEstado()).isEqualTo("TRASPASADO");

            service.recibir(id, "abigail");

            Producto destino = enTienda("CEL-000099", zocalo);
            assertThat(destino.getStock()).isEqualByComparingTo("2");
            assertThat(usado.getProducto()).isSameAs(destino);
            assertThat(usado.getEstado()).isEqualTo("DISPONIBLE");
            assertThat(usado.getCondicion()).isEqualTo("USADO");
            assertThat(usado.getCostoUnitario()).isEqualByComparingTo("1900");
            assertThat(a15.getStock()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("solo la tienda destino (o un ADMIN) recibe: otra tienda 403; una segunda recepción 409")
        void permisosYDobleRecepcion() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = service.crearEnvio(envio(1, 2, linea("ACC-000001", "5")), "root").getIdtraspaso();

            assertEstado(HttpStatus.FORBIDDEN, () -> service.recibir(id, "guillermo"));
            service.recibir(id, "abigail");
            assertEstado(HttpStatus.CONFLICT, () -> service.recibir(id, "abigail"));
            assertThat(enTienda("ACC-000099", zocalo).getStock()).isEqualByComparingTo("5");   // no se duplicó
        }

        @Test
        @DisplayName("una solicitud no se recibe: 400")
        void solicitudNoSeRecibe() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = service.crearSolicitud(solicitud(2, 1, linea("ACC-000001", "5")), "abigail").getIdtraspaso();

            assertThatThrownBy(() -> service.recibir(id, "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Solo se recibe un envío");
        }
    }

    // ── Anular ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("anular")
    class Anular {

        @Test
        @DisplayName("un envío sin recibir: el stock y las unidades regresan al origen y queda Anulado")
        void envioSinRecibir() {
            Producto a15 = stock("CEL-000004", almacen, celular, "2", negro);
            ProductoImei u1 = unidad(a15, "350000000000011");
            unidad(a15, "350000000000029");
            Producto funda = stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = service.crearEnvio(envio(1, 2, linea("CEL-000004", "1", "350000000000011"), linea("ACC-000001", "10")), "root").getIdtraspaso();
            assertThat(u1.getEstado()).isEqualTo("TRASPASADO");
            assertThat(funda.getStock()).isEqualByComparingTo("30");

            TraspasoResponseDto r = service.anular(id, "root");

            assertThat(u1.getEstado()).isEqualTo("DISPONIBLE");
            assertThat(a15.getStock()).isEqualByComparingTo("2");
            assertThat(funda.getStock()).isEqualByComparingTo("40");
            assertThat(r.getActivo()).isFalse();
            assertThat(r.getEstadoDisplay()).isEqualTo("Anulado");
        }

        @Test
        @DisplayName("un envío ya recibido no se puede anular (409); uno ya anulado, tampoco (409)")
        void noSePuede() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int recibido = service.crearEnvio(envio(1, 2, linea("ACC-000001", "5")), "root").getIdtraspaso();
            service.recibir(recibido, "abigail");
            int anulado = service.crearEnvio(envio(1, 2, linea("ACC-000001", "5")), "root").getIdtraspaso();
            service.anular(anulado, "root");

            assertEstado(HttpStatus.CONFLICT, () -> service.anular(recibido, "root"));
            assertEstado(HttpStatus.CONFLICT, () -> service.anular(anulado, "root"));
        }

        @Test
        @DisplayName("solo la tienda que lo creó lo anula: la destino recibe 403")
        void soloElOrigen() {
            stock("ACC-000001", zocalo, accesorio, "40", negro);
            int id = service.crearEnvio(envio(2, 3, linea("ACC-000001", "5")), "abigail").getIdtraspaso();

            assertEstado(HttpStatus.FORBIDDEN, () -> service.anular(id, "guillermo"));
        }

        @Test
        @DisplayName("un envío anulado ya no se puede recibir: 409")
        void anuladoNoSeRecibe() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = service.crearEnvio(envio(1, 2, linea("ACC-000001", "5")), "root").getIdtraspaso();
            service.anular(id, "root");

            assertEstado(HttpStatus.CONFLICT, () -> service.recibir(id, "abigail"));
        }
    }

    // ── Solicitud ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("solicitudes de reabastecimiento")
    class Solicitudes {

        private int pedir(TraspasoLineaRequestDto... lineas) {
            return service.crearSolicitud(solicitud(null, 1, lineas), "abigail").getIdtraspaso();
        }

        @Test
        @DisplayName("crear: queda Enviada de la tienda que pide hacia la que surte, sin mover stock")
        void crear() {
            Producto funda = stock("ACC-000001", almacen, accesorio, "40", negro);

            TraspasoResponseDto r = service.crearSolicitud(solicitud(null, 1, linea("ACC-000001", "12")), "abigail");

            assertThat(r.getTipo()).isEqualTo((byte) 2);
            assertThat(r.getCodtiOrigen()).isEqualTo(2);
            assertThat(r.getCodtiDestino()).isEqualTo(1);
            assertThat(r.getEstadoDisplay()).isEqualTo("Enviado");
            assertThat(r.getLineas().get(0).getNombreProducto()).isEqualTo("Funda Silicon");  // el código es del que surte
            assertThat(funda.getStock()).isEqualByComparingTo("40");
        }

        @Test
        @DisplayName("con IMEI, repetida, de un servicio o de un producto que no tiene el que surte: 400")
        void invalidas() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            stock("SRV-000001", almacen, servicio, "0", null);

            assertThatThrownBy(() -> pedir(linea("ACC-000001", "1", "350000000000011")))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("los elige");
            assertThatThrownBy(() -> pedir(linea("ACC-000001", "1"), linea("ACC-000001", "2")))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("repetido");
            assertThatThrownBy(() -> pedir(linea("SRV-000001", "1")))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("servicio");
            assertThatThrownBy(() -> pedir(linea("NO-EXISTE", "1")))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no existe");
        }

        @Test
        @DisplayName("leer: la tienda que surte la marca Leída (una sola vez); quien la pidió no puede (403)")
        void leer() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = pedir(linea("ACC-000001", "5"));

            assertEstado(HttpStatus.FORBIDDEN, () -> service.leer(id, "abigail"));
            TraspasoResponseDto r = service.leer(id, "root");
            assertThat(r.getEstado()).isEqualTo((byte) 3);
            assertThat(r.getEstadoDisplay()).isEqualTo("Leído");
            assertThat(service.leer(id, "root").getEstado()).isEqualTo((byte) 3);   // idempotente
        }

        @Test
        @DisplayName("aceptar: genera el envío ligado a la solicitud, sale el stock del que surte y la solicitud queda Aceptada")
        void aceptar() {
            Producto funda = stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = pedir(linea("ACC-000001", "12"));

            TraspasoResponseDto envio = service.aceptar(id, null, "root");

            assertThat(envio.getTipo()).isEqualTo((byte) 1);
            assertThat(envio.getIdtraspasoRef()).isEqualTo(id);
            assertThat(envio.getCodtiOrigen()).isEqualTo(1);     // surte el almacén
            assertThat(envio.getCodtiDestino()).isEqualTo(2);    // lo recibe quien pidió
            assertThat(funda.getStock()).isEqualByComparingTo("28");
            assertThat(traspasos.get(id).getEstado()).isEqualTo((byte) 4);
            assertThat(traspasos.get(id).getUsuarioValida()).isSameAs(root);

            // y la tienda que pidió lo recibe como cualquier envío
            service.recibir(envio.getIdtraspaso(), "abigail");
            assertThat(enTienda("ACC-000099", zocalo).getStock()).isEqualByComparingTo("12");
        }

        @Test
        @DisplayName("aceptar un pedido de equipos: hay que elegir los IMEI, y solo de lo pedido")
        void aceptarEquipos() {
            Producto a15 = stock("CEL-000004", almacen, celular, "3", negro);
            ProductoImei u1 = unidad(a15, "350000000000011");
            unidad(a15, "350000000000029");
            int id = pedir(linea("CEL-000004", "1"));

            assertThatThrownBy(() -> service.aceptar(id, null, "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("exactamente 1 IMEI");

            AceptarSolicitudRequestDto ajeno = new AceptarSolicitudRequestDto();
            AceptarSolicitudRequestDto.UnidadesElegidasDto otro = new AceptarSolicitudRequestDto.UnidadesElegidasDto();
            otro.setCodpro("CEL-000777");
            otro.setImeis(List.of("350000000000011"));
            ajeno.setEquipos(List.of(otro));
            assertThatThrownBy(() -> service.aceptar(id, ajeno, "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no está en la solicitud");

            AceptarSolicitudRequestDto ok = new AceptarSolicitudRequestDto();
            AceptarSolicitudRequestDto.UnidadesElegidasDto elegida = new AceptarSolicitudRequestDto.UnidadesElegidasDto();
            elegida.setCodpro("CEL-000004");
            elegida.setImeis(List.of("350000000000011"));
            ok.setEquipos(List.of(elegida));
            TraspasoResponseDto envio = service.aceptar(id, ok, "root");

            assertThat(u1.getEstado()).isEqualTo("TRASPASADO");
            assertThat(envio.getLineas().get(0).getImeis()).containsExactly("350000000000011");
        }

        @Test
        @DisplayName("rechazar: queda Rechazada con su motivo, sin mover stock; después ya no se puede aceptar (409)")
        void rechazar() {
            Producto funda = stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = pedir(linea("ACC-000001", "12"));
            RechazoRequestDto rechazo = new RechazoRequestDto();
            rechazo.setMotivo("  No hay existencia suficiente  ");

            TraspasoResponseDto r = service.rechazar(id, rechazo, "root");

            assertThat(r.getEstadoDisplay()).isEqualTo("Rechazada");
            assertThat(r.getMotivoRechazo()).isEqualTo("No hay existencia suficiente");
            assertThat(funda.getStock()).isEqualByComparingTo("40");
            assertEstado(HttpStatus.CONFLICT, () -> service.aceptar(id, null, "root"));
            assertEstado(HttpStatus.CONFLICT, () -> service.rechazar(id, rechazo, "root"));
        }

        @Test
        @DisplayName("aceptar o rechazar un envío (no una solicitud): 400")
        void envioNoSeAcepta() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int envio = service.crearEnvio(envio(1, 2, linea("ACC-000001", "5")), "root").getIdtraspaso();

            assertEstado(HttpStatus.BAD_REQUEST, () -> service.aceptar(envio, null, "abigail"));
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.leer(envio, "abigail"));
        }

        @Test
        @DisplayName("una solicitud pendiente se puede anular (sin tocar stock); resuelta, no (409)")
        void anular() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int pendiente = pedir(linea("ACC-000001", "5"));
            int resuelta = pedir(linea("ACC-000001", "5"));
            RechazoRequestDto rechazo = new RechazoRequestDto();
            rechazo.setMotivo("no");
            service.rechazar(resuelta, rechazo, "root");

            assertThat(service.anular(pendiente, "abigail").getEstadoDisplay()).isEqualTo("Anulado");
            assertEstado(HttpStatus.CONFLICT, () -> service.anular(resuelta, "abigail"));
        }
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("consultas")
    class Consultas {

        @Test
        @DisplayName("obtener: lo ven las tiendas involucradas y los administradores; una tercera tienda no (403)")
        void obtener() {
            stock("ACC-000001", almacen, accesorio, "40", negro);
            int id = service.crearEnvio(envio(1, 2, linea("ACC-000001", "5")), "root").getIdtraspaso();

            assertThat(service.obtener(id, "abigail").getIdtraspaso()).isEqualTo(id);
            assertThat(service.obtener(id, "root").getIdtraspaso()).isEqualTo(id);
            assertEstado(HttpStatus.FORBIDDEN, () -> service.obtener(id, "guillermo"));
            assertEstado(HttpStatus.NOT_FOUND, () -> service.obtener(9999, "root"));
        }

        @Test
        @DisplayName("listar y pendientes: solo de la tienda que el usuario opera")
        void permisosDeConsulta() {
            assertEstado(HttpStatus.FORBIDDEN, () -> service.listar(2, null, null, false, "guillermo"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.pendientes(2, "guillermo"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.listar(2, null, null, false, "yamilet"));
        }
    }
}
