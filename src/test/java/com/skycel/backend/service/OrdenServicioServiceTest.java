package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.ordenservicio.*;
import com.skycel.backend.dto.venta.VentaRequestDto;
import com.skycel.backend.dto.venta.VentaResponseDto;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Pruebas de OrdenServicioService sin Spring ni base de datos. Los repositorios se simulan con una
 * "base de datos" en memoria para recorrer el ciclo completo de una orden.
 */
@ExtendWith(MockitoExtension.class)
class OrdenServicioServiceTest {

    @Mock private OrdenServicioRepository          ordenRepository;
    @Mock private OrdenServicioDetalleRepository   detalleRepository;
    @Mock private OrdenServicioAnticipoRepository  anticipoRepository;
    @Mock private OrdenServicioHistorialRepository historialRepository;
    @Mock private TiendaRepository                 tiendaRepository;
    @Mock private ClienteRepository                clienteRepository;
    @Mock private UsuarioRepository                usuarioRepository;
    @Mock private ProductoRepository               productoRepository;
    @Mock private VentaRepository                  ventaRepository;
    @Mock private CategoriaFolioRepository         categoriaFolioRepository;
    @Mock private MovimientoCajaService            movimientoCajaService;
    @Mock private VentaService                     ventaService;
    @Mock private ClienteService                   clienteService;
    @Mock private NotificacionService              notificacionService;

    @InjectMocks
    private OrdenServicioService service;

    // "base de datos" en memoria
    private final Map<Integer, OrdenServicio> ordenes = new HashMap<>();
    private final List<OrdenServicioDetalle> detalles = new ArrayList<>();
    private final List<OrdenServicioAnticipo> anticipos = new ArrayList<>();
    private final List<OrdenServicioHistorial> historial = new ArrayList<>();
    private final List<Producto> productos = new ArrayList<>();
    private int id = 100;
    private long folio = 0;

    private Tienda zocalo, corpo;
    private Usuario root, encZocalo, vendZocalo, encCorpo, tecnico, otroTecnico, sinRol, tecnicoEncargado;
    private Cliente cliente;
    private ProductoMaster mServicio, mMica, mCelular;
    private Producto pantalla, mica, celular;
    private Caja cajaZocalo;

    @BeforeEach
    void setUp() {
        zocalo = Tienda.builder().codti(2).nombre("Zocalo").build();
        corpo  = Tienda.builder().codti(3).nombre("Corpo").build();
        root       = usuario(1, "root", Rol.ROOT, zocalo);
        encZocalo  = usuario(4, "abigail", Rol.ENCARGADO_TIENDA, zocalo);
        vendZocalo = usuario(9, "yamilet", Rol.VENDEDOR, zocalo);
        encCorpo   = usuario(5, "guillermo", Rol.ENCARGADO_TIENDA, corpo);
        tecnico    = usuario(20, "juan.perez", Rol.TECNICO, zocalo);
        otroTecnico = usuario(21, "ana.lopez", Rol.TECNICO, corpo);
        sinRol     = usuario(22, "oscar", Rol.VENDEDOR, corpo);
        tecnicoEncargado = usuario(23, "luis.tecnico", Rol.TECNICO, zocalo);
        tecnicoEncargado.setTecnicoEncargado(true);
        cliente = Cliente.builder().idcliente(7).nombreCompleto("Sofia Ramirez").telefono("7571200001").build();

        mServicio = ProductoMaster.builder().idprodmaster(1).tipo(TipoProducto.SERVICIO).nombreBase("Cambio de Pantalla").diasGarantia(30).build();
        mMica     = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.ACCESORIO).nombreBase("Mica Hidrogel").diasGarantia(15).build();
        mCelular  = ProductoMaster.builder().idprodmaster(3).tipo(TipoProducto.CELULAR).nombreBase("iPhone 15").build();
        pantalla = producto("SRV-000001", mServicio, "0", "1200");
        mica     = producto("ACC-000010", mMica, "10", "90");
        celular  = producto("CEL-000001", mCelular, "3", "15999");
        cajaZocalo = Caja.builder().idCaja(1).nombreCaja("Caja Zocalo").tienda(zocalo).build();

        for (Tienda t : List.of(zocalo, corpo)) lenient().when(tiendaRepository.findById(t.getCodti())).thenReturn(Optional.of(t));
        lenient().when(clienteRepository.findById(7)).thenReturn(Optional.of(cliente));
        for (Usuario u : List.of(root, encZocalo, vendZocalo, encCorpo, tecnico, otroTecnico, sinRol, tecnicoEncargado)) {
            lenient().when(usuarioRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));
            lenient().when(usuarioRepository.findById(u.getIdusuario())).thenReturn(Optional.of(u));
        }
        lenient().when(categoriaFolioRepository.obtenerUltimoFolioGenerado((short) -1)).thenAnswer(inv -> ++folio);

        lenient().when(ordenRepository.save(any(OrdenServicio.class))).thenAnswer(inv -> {
            OrdenServicio o = inv.getArgument(0);
            if (o.getIdorden() == null) { o.setIdorden(id++); if (o.getFechaIngreso() == null) o.setFechaIngreso(LocalDateTime.now()); }
            ordenes.put(o.getIdorden(), o);
            return o;
        });
        lenient().when(ordenRepository.findByClaveOffline(any())).thenAnswer(inv ->
                ordenes.values().stream().filter(o -> inv.getArgument(0).equals(o.getClaveOffline())).findFirst());
        lenient().when(ordenRepository.findById(anyInt())).thenAnswer(inv -> Optional.ofNullable(ordenes.get((Integer) inv.getArgument(0))));
        lenient().when(ordenRepository.findByFolio(any())).thenAnswer(inv ->
                ordenes.values().stream().filter(o -> o.getFolio().equals(inv.getArgument(0))).findFirst());
        lenient().when(detalleRepository.save(any(OrdenServicioDetalle.class))).thenAnswer(inv -> {
            OrdenServicioDetalle d = inv.getArgument(0);
            if (d.getIddetalle() == null) { d.setIddetalle(id++); detalles.add(d); }
            return d;
        });
        lenient().when(detalleRepository.findById(anyInt())).thenAnswer(inv ->
                detalles.stream().filter(d -> d.getIddetalle().equals(inv.getArgument(0))).findFirst());
        lenient().doAnswer(inv -> { detalles.remove((OrdenServicioDetalle) inv.getArgument(0)); return null; })
                .when(detalleRepository).delete(any(OrdenServicioDetalle.class));
        lenient().when(detalleRepository.findByOrden_IdordenOrderByIddetalle(anyInt())).thenAnswer(inv ->
                detalles.stream().filter(d -> d.getOrden().getIdorden().equals(inv.getArgument(0))).collect(Collectors.toList()));
        lenient().when(anticipoRepository.save(any(OrdenServicioAnticipo.class))).thenAnswer(inv -> {
            OrdenServicioAnticipo a = inv.getArgument(0);
            if (a.getIdanticipo() == null) { a.setIdanticipo(id++); anticipos.add(a); }
            return a;
        });
        lenient().when(anticipoRepository.findByOrden_IdordenOrderByIdanticipo(anyInt())).thenAnswer(inv ->
                anticipos.stream().filter(a -> a.getOrden().getIdorden().equals(inv.getArgument(0))).collect(Collectors.toList()));
        lenient().when(historialRepository.save(any(OrdenServicioHistorial.class))).thenAnswer(inv -> {
            historial.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(historialRepository.findByOrden_IdordenOrderByIdhistorial(anyInt())).thenAnswer(inv ->
                historial.stream().filter(h -> h.getOrden().getIdorden().equals(inv.getArgument(0))).collect(Collectors.toList()));

        lenient().when(productoRepository.findByCodproAndTienda_Codti(any(), anyInt())).thenAnswer(inv ->
                productos.stream().filter(p -> p.getCodpro().equals(inv.getArgument(0)) && p.getTienda().getCodti().equals(inv.getArgument(1))).findFirst());
        lenient().when(movimientoCajaService.cajaParaTienda(any(), any(Tienda.class))).thenReturn(cajaZocalo);
        lenient().when(movimientoCajaService.registrarAnticipoServicio(any(), any(OrdenServicio.class), any(OrdenServicioAnticipo.class), any(Usuario.class))).thenReturn(900);
        lenient().when(ventaService.crearDesdeOrdenServicio(any(VentaRequestDto.class), anyInt()))
                .thenReturn(VentaResponseDto.builder().idventa(55).build());
        lenient().when(ventaRepository.findById(55)).thenReturn(Optional.of(Venta.builder().idventa(55).build()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Usuario usuario(int idu, String username, Rol rol, Tienda t) {
        return Usuario.builder().idusuario(idu).username(username).nombreCompleto(username.toUpperCase()).rol(rol).tienda(t).activo(true).build();
    }

    private Producto producto(String codpro, ProductoMaster m, String stock, String precio) {
        Producto p = Producto.builder().idproducto(id++).codpro(codpro).tienda(zocalo).productoMaster(m)
                .stock(new BigDecimal(stock)).preciopub(new BigDecimal(precio)).preciopro(new BigDecimal("10")).activo(true).build();
        productos.add(p);
        return p;
    }

    private OrdenServicioRequestDto recepcion() {
        OrdenServicioRequestDto dto = new OrdenServicioRequestDto();
        dto.setIdcliente(7);
        dto.setMarca("Apple");
        dto.setModelo("iPhone 13");
        dto.setFallaReportada("Pantalla estrellada");
        return dto;
    }

    private OrdenLineaRequestDto linea(String codpro, Integer cantidad, String precio, Boolean regalo) {
        OrdenLineaRequestDto l = new OrdenLineaRequestDto();
        l.setCodpro(codpro);
        l.setCantidad(cantidad == null ? null : cantidad.shortValue());
        l.setPrecioUnitario(precio == null ? null : new BigDecimal(precio));
        l.setEsRegalo(regalo);
        return l;
    }

    private AnticipoRequestDto anticipo(String monto, int metodo) {
        AnticipoRequestDto a = new AnticipoRequestDto();
        a.setMonto(new BigDecimal(monto));
        a.setMetodoPago((byte) metodo);
        return a;
    }

    /** Recibe una orden (como encargado) con el técnico asignado y los renglones indicados. */
    private int orden(OrdenLineaRequestDto... lineas) {
        OrdenServicioRequestDto dto = recepcion();
        dto.setLineas(lineas.length == 0 ? null : List.of(lineas));
        int idorden = service.crear(dto, "abigail").getIdorden();
        ordenes.get(idorden).setTecnico(tecnico);   // la asignación la hace el técnico encargado o un administrador
        return idorden;
    }

    private OrdenServicio viva(int idorden) {
        return ordenes.get(idorden);
    }

    private void assertEstado(HttpStatus esperado, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    private EntregaRequestDto entrega(Integer metodo) {
        EntregaRequestDto e = new EntregaRequestDto();
        e.setMetodoPago(metodo == null ? null : metodo.byteValue());
        return e;
    }

    /** Lleva la orden hasta "Lista para entrega". */
    private void hastaLista(int idorden) {
        service.iniciar(idorden, "juan.perez");
        service.marcarLista(idorden, "juan.perez");
    }

    // ── Recibir ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recibir un equipo sin conexión")
    class RecibirSinConexion {

        private OrdenServicioRequestDto sinConexion(String clave) {
            OrdenServicioRequestDto d = recepcion();
            d.setClaveOffline(clave);
            d.setFolioLocal("OFF-ZOC-000001");
            d.setFechaRecepcion(LocalDateTime.now().minusHours(3).withNano(0));
            return d;
        }

        @Test
        @DisplayName("conserva la fecha original y el folio provisional")
        void conservaFechaYFolio() {
            OrdenServicioRequestDto d = sinConexion("clave-1");
            OrdenServicioResponseDto r = service.crear(d, "abigail");

            assertThat(r.getFolioLocal()).isEqualTo("OFF-ZOC-000001");
            assertThat(ordenes.get(r.getIdorden()).getFechaIngreso()).isEqualTo(d.getFechaRecepcion());
            assertThat(r.getHistorial().get(0).getComentario()).contains("sin conexión").contains("OFF-ZOC-000001");
        }

        @Test
        @DisplayName("reenviar la misma clave devuelve la misma orden y no duplica")
        void idempotente() {
            OrdenServicioResponseDto a = service.crear(sinConexion("clave-2"), "abigail");
            OrdenServicioResponseDto b = service.crear(sinConexion("clave-2"), "abigail");

            assertThat(b.getIdorden()).isEqualTo(a.getIdorden());
            assertThat(ordenes).hasSize(1);
        }

        @Test
        @DisplayName("rechaza fechas futuras y de hace más de 45 días")
        void fechas() {
            OrdenServicioRequestDto futura = sinConexion("clave-3");
            futura.setFechaRecepcion(LocalDateTime.now().plusHours(2));
            assertThatThrownBy(() -> service.crear(futura, "abigail")).hasMessageContaining("futura");

            OrdenServicioRequestDto vieja = sinConexion("clave-4");
            vieja.setFechaRecepcion(LocalDateTime.now().minusDays(46));
            assertThatThrownBy(() -> service.crear(vieja, "abigail")).hasMessageContaining("45 días");
        }

        @Test
        @DisplayName("el anticipo conserva la fecha real de la recepción, no la de sincronización")
        void anticipoConservaFecha() {
            OrdenServicioRequestDto d = sinConexion("clave-anticipo");
            d.setAnticipo(anticipo("300", 1));

            OrdenServicioResponseDto r = service.crear(d, "abigail");

            assertThat(r.getAnticipos()).hasSize(1);
            assertThat(r.getAnticipos().get(0).getFecha()).isEqualTo(d.getFechaRecepcion());
        }

        @Test
        @DisplayName("crea el cliente por teléfono si no existía, y sin clave no se acepta cliente nuevo")
        void clienteNuevo() {
            Cliente nuevo = Cliente.builder().idcliente(50).nombreCompleto("Cliente Nuevo").telefono("7570000000").build();
            lenient().when(clienteRepository.findByTelefono("7570000000")).thenReturn(Optional.empty());
            com.skycel.backend.dto.cliente.ClienteResponseDto resp = com.skycel.backend.dto.cliente.ClienteResponseDto.builder().idcliente(50).build();
            lenient().when(clienteService.crear(any())).thenReturn(resp);
            lenient().when(clienteRepository.findById(50)).thenReturn(Optional.of(nuevo));

            OrdenServicioRequestDto d = sinConexion("clave-5");
            d.setIdcliente(null); d.setClienteNombre("Cliente Nuevo"); d.setClienteTelefono("7570000000");
            assertThat(service.crear(d, "abigail").getNombreCliente()).isEqualTo("Cliente Nuevo");

            OrdenServicioRequestDto sinClave = recepcion();
            sinClave.setIdcliente(null); sinClave.setClienteNombre("X"); sinClave.setClienteTelefono("1");
            assertThatThrownBy(() -> service.crear(sinClave, "abigail")).hasMessageContaining("cliente es obligatorio");
        }
    }

    @Nested
    @DisplayName("recibir un equipo")
    class Recibir {

        @Test
        @DisplayName("crea la orden Recibida con folio OS-000001, en la tienda del usuario, y anota la recepción en la bitácora")
        void crea() {
            OrdenServicioResponseDto r = service.crear(recepcion(), "abigail");

            assertThat(r.getFolio()).isEqualTo("OS-000001");
            assertThat(r.getEstado()).isEqualTo((byte) 1);
            assertThat(r.getEstadoDisplay()).isEqualTo("Recibida");
            assertThat(r.getCodti()).isEqualTo(2);
            assertThat(r.getNombreCliente()).isEqualTo("Sofia Ramirez");
            assertThat(r.getNombreRecibe()).isEqualTo("ABIGAIL");
            assertThat(r.getTotal()).isEqualByComparingTo("0");
            assertThat(r.getHistorial()).hasSize(1);
            assertThat(r.getHistorial().get(0).getComentario()).isEqualTo("Equipo recibido");
        }

        @Test
        @DisplayName("los folios son consecutivos")
        void folios() {
            assertThat(service.crear(recepcion(), "abigail").getFolio()).isEqualTo("OS-000001");
            assertThat(service.crear(recepcion(), "abigail").getFolio()).isEqualTo("OS-000002");
        }

        @Test
        @DisplayName("con técnico, renglones y anticipo en efectivo: calcula total y saldo, y el efectivo entra a caja")
        void completa() {
            OrdenServicioRequestDto dto = recepcion();
            dto.setIdTecnico(20);
            dto.setLineas(List.of(linea("SRV-000001", null, null, null), linea("ACC-000010", 2, null, null)));
            dto.setAnticipo(anticipo("500", 1));
            dto.getAnticipo().setIdCaja(1);

            assertEstado(HttpStatus.FORBIDDEN, () -> service.crear(dto, "abigail"));   // el encargado de la tienda no asigna técnico
            OrdenServicioResponseDto r = service.crear(dto, "root");

            assertThat(r.getNombreTecnico()).isEqualTo("JUAN.PEREZ");
            assertThat(r.getLineas()).hasSize(2);
            assertThat(r.getLineas().get(0).getNombre()).isEqualTo("Cambio de Pantalla");
            assertThat(r.getLineas().get(0).getCantidad()).isEqualTo((short) 1);              // cantidad por defecto
            assertThat(r.getLineas().get(1).getSubtotal()).isEqualByComparingTo("180");        // 2 × 90
            assertThat(r.getTotal()).isEqualByComparingTo("1380");
            assertThat(r.getTotalAnticipos()).isEqualByComparingTo("500");
            assertThat(r.getSaldo()).isEqualByComparingTo("880");
            assertThat(r.getAnticipos().get(0).getIdmovimientoCaja()).isEqualTo(900);
            verify(movimientoCajaService).registrarAnticipoServicio(eq(1), any(OrdenServicio.class), any(OrdenServicioAnticipo.class), eq(root));
        }

        @Test
        @DisplayName("un anticipo con tarjeta o transferencia no toca la caja")
        void anticipoSinEfectivo() {
            OrdenServicioRequestDto dto = recepcion();
            dto.setAnticipo(anticipo("300", 2));

            OrdenServicioResponseDto r = service.crear(dto, "abigail");

            assertThat(r.getTotalAnticipos()).isEqualByComparingTo("300");
            assertThat(r.getAnticipos().get(0).getIdmovimientoCaja()).isNull();
            verify(movimientoCajaService, never()).registrarAnticipoServicio(any(), any(), any(), any());
        }

        @Test
        @DisplayName("método de anticipo no válido (PayJoy): 400")
        void anticipoMetodoInvalido() {
            OrdenServicioRequestDto dto = recepcion();
            dto.setAnticipo(anticipo("300", 5));

            assertThatThrownBy(() -> service.crear(dto, "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Método del anticipo no válido");
        }

        @Test
        @DisplayName("técnico que no existe, sin el rol TECNICO o inactivo: 400")
        void tecnicoInvalido() {
            OrdenServicioRequestDto noExiste = recepcion();
            noExiste.setIdTecnico(999);
            assertThatThrownBy(() -> service.crear(noExiste, "root")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("no encontrado");

            OrdenServicioRequestDto vendedor = recepcion();
            vendedor.setIdTecnico(9);
            assertThatThrownBy(() -> service.crear(vendedor, "root")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("no tiene el rol TECNICO");

            tecnico.setActivo(false);
            OrdenServicioRequestDto inactivo = recepcion();
            inactivo.setIdTecnico(20);
            assertThatThrownBy(() -> service.crear(inactivo, "root")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("inactivo");
        }

        @Test
        @DisplayName("cliente inexistente, fecha prometida pasada o IMEI inválido: 400")
        void datosInvalidos() {
            OrdenServicioRequestDto cli = recepcion();
            cli.setIdcliente(999);
            assertThatThrownBy(() -> service.crear(cli, "abigail")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Cliente no encontrado");

            OrdenServicioRequestDto fecha = recepcion();
            fecha.setFechaPromesa(LocalDate.now().minusDays(1));
            assertThatThrownBy(() -> service.crear(fecha, "abigail")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("anterior a hoy");

            OrdenServicioRequestDto imei = recepcion();
            imei.setImei("12 34");
            assertThatThrownBy(() -> service.crear(imei, "abigail")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("5 y 20");
        }

        @Test
        @DisplayName("permisos: un encargado no recibe en la tienda de otro (403); un técnico no recibe equipos (403)")
        void permisos() {
            OrdenServicioRequestDto enCorpo = recepcion();
            enCorpo.setCodti(3);
            assertEstado(HttpStatus.FORBIDDEN, () -> service.crear(enCorpo, "abigail"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.crear(recepcion(), "juan.perez"));
            enCorpo.setCodti(3);
            assertThat(service.crear(enCorpo, "root").getCodti()).isEqualTo(3);   // un administrador sí
        }
    }

    // ── Renglones ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("servicios y refacciones (renglones)")
    class Renglones {

        @Test
        @DisplayName("un equipo o un producto que no existe en la tienda no se puede agregar: 400")
        void productosInvalidos() {
            int o = orden();

            assertThatThrownBy(() -> service.agregarLinea(o, linea("CEL-000001", 1, null, null), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Un equipo no se agrega");
            assertThatThrownBy(() -> service.agregarLinea(o, linea("NO-EXISTE", 1, null, null), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no existe en la tienda");
        }

        @Test
        @DisplayName("precio personalizado; y $0 solo si es regalo")
        void precios() {
            int o = orden();

            OrdenServicioResponseDto r = service.agregarLinea(o, linea("SRV-000001", 1, "1000", null), "root");
            assertThat(r.getLineas().get(0).getPrecioUnitario()).isEqualByComparingTo("1000");
            assertThatThrownBy(() -> service.agregarLinea(o, linea("ACC-000010", 1, "0", null), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("solo se permite en artículos marcados como regalo");
        }

        @Test
        @DisplayName("regalo: un administrador lo autoriza ($0 aunque no se indique precio); vendedor y técnico no (403)")
        void regalo() {
            int o = orden();

            OrdenServicioResponseDto r = service.agregarLinea(o, linea("ACC-000010", 1, null, true), "root");
            assertThat(r.getLineas().get(0).getPrecioUnitario()).isEqualByComparingTo("0");
            assertThat(r.getLineas().get(0).getEsRegalo()).isTrue();
            assertEstado(HttpStatus.FORBIDDEN, () -> service.agregarLinea(o, linea("ACC-000010", 1, null, true), "yamilet"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.agregarLinea(o, linea("ACC-000010", 1, null, true), "juan.perez"));
            assertThatThrownBy(() -> service.agregarLinea(o, linea("ACC-000010", 1, "50", true), "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("debe tener precio $0");
        }

        @Test
        @DisplayName("se puede quitar un renglón; uno que no es de la orden da 404")
        void quitar() {
            int o = orden(linea("SRV-000001", null, null, null));
            int iddetalle = service.obtener(o, "root").getLineas().get(0).getIddetalle();

            assertEstado(HttpStatus.NOT_FOUND, () -> service.eliminarLinea(o, 99999, "root"));
            assertThat(service.eliminarLinea(o, iddetalle, "root").getLineas()).isEmpty();
        }

        @Test
        @DisplayName("ya lista, la orden no admite más renglones (409)")
        void ordenLista() {
            int o = orden(linea("SRV-000001", null, null, null));
            hastaLista(o);

            assertEstado(HttpStatus.CONFLICT, () -> service.agregarLinea(o, linea("ACC-000010", 1, null, null), "root"));
            assertEstado(HttpStatus.CONFLICT, () -> service.eliminarLinea(o, 1, "root"));
        }
    }

    // ── Ciclo del taller ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("ciclo de la reparación")
    class Ciclo {

        @Test
        @DisplayName("iniciar exige técnico asignado; se inicia una sola vez (409 después)")
        void iniciar() {
            int sinTecnico = service.crear(recepcion(), "abigail").getIdorden();
            assertThatThrownBy(() -> service.iniciar(sinTecnico, "root"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Asigne un técnico");

            int o = orden();
            assertThat(service.iniciar(o, "juan.perez").getEstadoDisplay()).isEqualTo("En reparación");
            assertEstado(HttpStatus.CONFLICT, () -> service.iniciar(o, "juan.perez"));
        }

        @Test
        @DisplayName("marcar lista exige renglones y stock de las refacciones; luego queda Lista con su fecha")
        void lista() {
            int vacia = orden();
            service.iniciar(vacia, "juan.perez");
            assertThatThrownBy(() -> service.marcarLista(vacia, "juan.perez"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("al menos un servicio");

            int sinStock = orden(linea("SRV-000001", null, null, null), linea("ACC-000010", 50, null, null));
            service.iniciar(sinStock, "juan.perez");
            assertThatThrownBy(() -> service.marcarLista(sinStock, "juan.perez"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Falta stock de la refacción 'Mica Hidrogel'");

            int ok = orden(linea("SRV-000001", null, null, null), linea("ACC-000010", 2, null, null));
            service.iniciar(ok, "juan.perez");
            OrdenServicioResponseDto r = service.marcarLista(ok, "juan.perez");
            assertThat(r.getEstadoDisplay()).isEqualTo("Lista para entrega");
            assertThat(r.getFechaLista()).isNotNull();
        }

        @Test
        @DisplayName("marcar lista sin haber iniciado: 409")
        void listaSinIniciar() {
            int o = orden(linea("SRV-000001", null, null, null));

            assertEstado(HttpStatus.CONFLICT, () -> service.marcarLista(o, "juan.perez"));
        }

        @Test
        @DisplayName("reabrir una orden lista la devuelve a reparación (con comentario); en otro estado, 409")
        void reabrir() {
            int o = orden(linea("SRV-000001", null, null, null));
            ComentarioRequestDto motivo = new ComentarioRequestDto();
            motivo.setComentario("El cliente reporta que la bocina tambien falla");
            assertEstado(HttpStatus.CONFLICT, () -> service.reabrir(o, motivo, "juan.perez"));

            hastaLista(o);
            OrdenServicioResponseDto r = service.reabrir(o, motivo, "juan.perez");

            assertThat(r.getEstadoDisplay()).isEqualTo("En reparación");
            assertThat(r.getFechaLista()).isNull();
            assertThat(r.getHistorial().get(r.getHistorial().size() - 1).getComentario()).contains("la bocina tambien falla");
        }

        @Test
        @DisplayName("diagnóstico y datos: se pueden actualizar mientras la orden esté abierta; entregada, 409")
        void actualizar() {
            int o = orden();
            OrdenActualizarDto dto = new OrdenActualizarDto();
            dto.setDiagnostico("  Display roto y flex dañado  ");
            dto.setFechaPromesa(LocalDate.now().plusDays(3));

            OrdenServicioResponseDto r = service.actualizar(o, dto, "juan.perez");

            assertThat(r.getDiagnostico()).isEqualTo("Display roto y flex dañado");
            assertThat(r.getFechaPromesa()).isEqualTo(LocalDate.now().plusDays(3));
            assertThat(r.getHistorial().get(r.getHistorial().size() - 1).getComentario()).isEqualTo("Diagnóstico registrado");

            viva(o).setEstado((byte) 4);
            assertEstado(HttpStatus.CONFLICT, () -> service.actualizar(o, dto, "abigail"));
        }

        @Test
        @DisplayName("asignar técnico: lo hace el técnico encargado (de su tienda) o un administrador; ni el encargado de la tienda ni un vendedor ni otro técnico (403)")
        void asignarTecnico() {
            int o = service.crear(recepcion(), "abigail").getIdorden();
            AsignarTecnicoDto dto = new AsignarTecnicoDto();
            dto.setIdTecnico(20);

            assertEstado(HttpStatus.FORBIDDEN, () -> service.asignarTecnico(o, dto, "yamilet"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.asignarTecnico(o, dto, "abigail"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.asignarTecnico(o, dto, "juan.perez"));
            OrdenServicioResponseDto r = service.asignarTecnico(o, dto, "luis.tecnico");
            assertThat(r.getNombreTecnico()).isEqualTo("JUAN.PEREZ");
            assertThat(r.getHistorial().get(r.getHistorial().size() - 1).getComentario()).contains("Técnico asignado");
            assertThat(service.asignarTecnico(o, dto, "root").getNombreTecnico()).isEqualTo("JUAN.PEREZ");
        }

        @Test
        @DisplayName("el taller atiende a todas las sucursales: el técnico encargado asigna a cualquier técnico, de cualquier tienda")
        void tecnicoEncargadoTodasLasTiendas() {
            int o = service.crear(recepcion(), "abigail").getIdorden();   // recibida en Zócalo
            AsignarTecnicoDto deOtraTienda = new AsignarTecnicoDto();
            deOtraTienda.setIdTecnico(21);   // ana.lopez, registrada en Corpo

            assertThat(service.asignarTecnico(o, deOtraTienda, "luis.tecnico").getNombreTecnico()).isEqualTo("ANA.LOPEZ");
        }

        @Test
        @DisplayName("tomar: un técnico toma una orden libre (recoge el equipo) y queda asignada; con el encargado ausente, cualquiera puede")
        void tomar() {
            int o = service.crear(recepcion(), "abigail").getIdorden();

            assertEstado(HttpStatus.FORBIDDEN, () -> service.tomar(o, "abigail"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.tomar(o, "yamilet"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.iniciar(o, "juan.perez"));   // antes de tomarla no se trabaja
            OrdenServicioResponseDto r = service.tomar(o, "ana.lopez");   // un técnico de otra tienda, en el taller

            assertThat(r.getNombreTecnico()).isEqualTo("ANA.LOPEZ");
            assertThat(r.getHistorial().get(r.getHistorial().size() - 1).getComentario()).contains("Equipo recogido en Zocalo").contains("taller");
            assertEstado(HttpStatus.CONFLICT, () -> service.tomar(o, "juan.perez"));   // ya la tomó otro
            assertEstado(HttpStatus.CONFLICT, () -> service.tomar(o, "ana.lopez"));
            assertThat(service.iniciar(o, "ana.lopez").getEstadoDisplay()).isEqualTo("En reparación");
            assertEstado(HttpStatus.CONFLICT, () -> service.tomar(o, "luis.tecnico"));   // ya no está recibida
        }

        @Test
        @DisplayName("taller: el administrador y el técnico encargado ven todas las abiertas; un técnico, las libres y las suyas; el mostrador no")
        void taller() {
            OrdenServicio libre = OrdenServicio.builder().idorden(1).folio("OS-1").tienda(zocalo).cliente(Cliente.builder().idcliente(7).nombreCompleto("Sofia").build()).usuarioRecibe(encZocalo).estado((byte) 1).build();
            OrdenServicio mia = OrdenServicio.builder().idorden(2).folio("OS-2").tienda(corpo).cliente(Cliente.builder().idcliente(7).nombreCompleto("Sofia").build()).usuarioRecibe(encCorpo).tecnico(tecnico).estado((byte) 2).build();
            when(ordenRepository.abiertasDeTodas()).thenReturn(List.of(libre, mia));
            when(ordenRepository.abiertasPorTomarODelTecnico(20)).thenReturn(List.of(libre, mia));
            when(ordenRepository.abiertasPorTomarODelTecnico(21)).thenReturn(List.of(libre));
            lenient().when(anticipoRepository.findByOrden_IdordenOrderByIdanticipo(anyInt())).thenReturn(List.of());

            assertThat(service.taller("root")).hasSize(2);
            assertThat(service.taller("luis.tecnico")).hasSize(2);
            assertThat(service.taller("juan.perez")).hasSize(2);
            assertThat(service.taller("ana.lopez")).hasSize(1);
            assertEstado(HttpStatus.FORBIDDEN, () -> service.taller("abigail"));
        }

        @Test
        @DisplayName("el taller (estados, diagnóstico, renglones y observaciones) es de administradores y técnicos: el personal de mostrador, 403")
        void soloAdminYTecnicos() {
            int o = orden(linea("SRV-000001", null, null, null));
            ComentarioRequestDto nota = new ComentarioRequestDto();
            nota.setComentario("El cliente pidió que no se borren los datos");
            OrdenActualizarDto diag = new OrdenActualizarDto();
            diag.setDiagnostico("Flex dañado");

            for (String quien : List.of("abigail", "yamilet")) {
                assertEstado(HttpStatus.FORBIDDEN, () -> service.iniciar(o, quien));
                assertEstado(HttpStatus.FORBIDDEN, () -> service.agregarNota(o, nota, quien));
                assertEstado(HttpStatus.FORBIDDEN, () -> service.actualizar(o, diag, quien));
                assertEstado(HttpStatus.FORBIDDEN, () -> service.agregarLinea(o, linea("ACC-000010", 1, null, null), quien));
                assertEstado(HttpStatus.FORBIDDEN, () -> service.eliminarLinea(o, 1, quien));
            }
            // el personal sí corrige los datos de recepción (no el diagnóstico)
            OrdenActualizarDto datos = new OrdenActualizarDto();
            datos.setModelo("iPhone 13 Pro");
            assertThat(service.actualizar(o, datos, "abigail").getModelo()).isEqualTo("iPhone 13 Pro");

            assertThat(service.agregarNota(o, nota, "juan.perez").getHistorial()).extracting(OrdenServicioResponseDto.HistorialResponseDto::getComentario)
                    .anyMatch(c -> c.startsWith("Observación: El cliente pidió"));
            assertThat(service.iniciar(o, "root").getEstadoDisplay()).isEqualTo("En reparación");
            assertThat(service.actualizar(o, diag, "luis.tecnico").getDiagnostico()).isEqualTo("Flex dañado");
        }

        @Test
        @DisplayName("el técnico encargado ve y trabaja todas las órdenes (de cualquier sucursal); otro técnico solo las suyas y las libres")
        void tecnicoEncargadoVeTodo() {
            int o = orden(linea("SRV-000001", null, null, null));   // asignada a juan.perez, recibida en Zócalo

            assertThat(service.obtener(o, "luis.tecnico").getIdorden()).isEqualTo(o);
            when(ordenRepository.buscar(2, null, null)).thenReturn(List.of(viva(o)));
            assertThat(service.listar(2, null, null, "luis.tecnico")).hasSize(1);
            assertThat(service.listar(3, null, null, "luis.tecnico")).isEmpty();   // también las de otras tiendas
            assertEstado(HttpStatus.FORBIDDEN, () -> service.listar(2, null, null, "juan.perez"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.obtener(o, "ana.lopez"));   // asignada a otro técnico
            assertThat(service.iniciar(o, "luis.tecnico").getEstadoDisplay()).isEqualTo("En reparación");
        }
    }

    // ── Permisos por rol ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("permisos: personal de la tienda y técnicos")
    class Permisos {

        @Test
        @DisplayName("el técnico asignado ve y trabaja su orden; otro técnico no (403)")
        void tecnicoAsignado() {
            int o = orden(linea("SRV-000001", null, null, null));

            assertThat(service.obtener(o, "juan.perez").getFolio()).isEqualTo("OS-000001");
            assertEstado(HttpStatus.FORBIDDEN, () -> service.obtener(o, "ana.lopez"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.iniciar(o, "ana.lopez"));
        }

        @Test
        @DisplayName("el técnico no recibe dinero, no entrega ni cancela (403)")
        void tecnicoNoCobra() {
            int o = orden(linea("SRV-000001", null, null, null));
            hastaLista(o);
            CancelarOrdenRequestDto cancelar = new CancelarOrdenRequestDto();
            cancelar.setMotivo("x");

            assertEstado(HttpStatus.FORBIDDEN, () -> service.registrarAnticipo(o, anticipo("100", 1), "juan.perez"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.entregar(o, entrega(1), "juan.perez"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.cancelar(o, cancelar, "juan.perez"));
        }

        @Test
        @DisplayName("el personal de otra tienda no ve ni opera la orden (403)")
        void otraTienda() {
            int o = orden(linea("SRV-000001", null, null, null));

            assertEstado(HttpStatus.FORBIDDEN, () -> service.obtener(o, "guillermo"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.registrarAnticipo(o, anticipo("100", 1), "oscar"));
            assertThat(service.obtener(o, "root").getIdorden()).isEqualTo(o);
        }

        @Test
        @DisplayName("consultas: un técnico no lista por tienda (403); mis-ordenes es solo de técnicos")
        void consultas() {
            assertEstado(HttpStatus.FORBIDDEN, () -> service.listar(2, null, null, "juan.perez"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.listar(2, null, null, "guillermo"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.misOrdenes("abigail"));
            when(ordenRepository.abiertasDelTecnico(20)).thenReturn(List.of());
            assertThat(service.misOrdenes("juan.perez")).isEmpty();
        }

        @Test
        @DisplayName("por folio (sin importar mayúsculas)")
        void porFolio() {
            int o = orden();

            assertThat(service.obtenerPorFolio(" os-000001 ", "abigail").getIdorden()).isEqualTo(o);
            assertEstado(HttpStatus.NOT_FOUND, () -> service.obtenerPorFolio("OS-999999", "abigail"));
        }
    }

    // ── Entregar ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("entregar y cobrar (genera la venta)")
    class Entregar {

        private VentaRequestDto ventaGenerada() {
            ArgumentCaptor<VentaRequestDto> cap = ArgumentCaptor.forClass(VentaRequestDto.class);
            verify(ventaService).crearDesdeOrdenServicio(cap.capture(), anyInt());
            return cap.getValue();
        }

        private int listaConPantallaYMica() {
            int o = orden(linea("SRV-000001", null, null, null), linea("ACC-000010", 2, null, null));   // 1200 + 180 = 1380
            hastaLista(o);
            return o;
        }

        @Test
        @DisplayName("sin anticipos: la venta lleva los renglones y el método elegido; la orden queda Entregada con su venta y garantía")
        void sinAnticipos() {
            int o = listaConPantallaYMica();
            EntregaRequestDto dto = entrega(2);
            dto.setObservaciones("Entrega con voucher");

            OrdenServicioResponseDto r = service.entregar(o, dto, "yamilet");

            VentaRequestDto v = ventaGenerada();
            assertThat(v.getMetodoPago()).isEqualTo((byte) 2);
            assertThat(v.getPagos()).isNull();
            assertThat(v.getCodti()).isEqualTo(2);
            assertThat(v.getIdCaja()).isEqualTo(1);
            assertThat(v.getIdcliente()).isEqualTo(7);
            assertThat(v.getObservaciones()).isEqualTo("Orden de servicio OS-000001. Entrega con voucher");
            assertThat(v.getDetalles()).hasSize(2);
            assertThat(v.getDetalles().get(0).getCodpro()).isEqualTo("SRV-000001");
            assertThat(v.getDetalles().get(0).getPrecioUnitarioFinal()).isEqualByComparingTo("1200");
            assertThat(v.getDetalles().get(1).getCantidad()).isEqualTo((short) 2);

            assertThat(r.getEstadoDisplay()).isEqualTo("Entregada");
            assertThat(r.getIdventa()).isEqualTo(55);
            assertThat(r.getFechaEntrega()).isNotNull();
            assertThat(r.getDiasGarantia()).isEqualTo(30);                                   // el mayor entre pantalla (30) y mica (15)
            assertThat(r.getFechaGarantiaHasta()).isEqualTo(LocalDate.now().plusDays(30));
        }

        @Test
        @DisplayName("sin anticipos y sin indicar cómo paga: 400")
        void sinMetodo() {
            int o = listaConPantallaYMica();

            assertThatThrownBy(() -> service.entregar(o, entrega(null), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Indique cómo se paga");
            verify(ventaService, never()).crearDesdeOrdenServicio(any(), anyInt());
        }

        @Test
        @DisplayName("con anticipo parcial: pago Mixto = línea ANTICIPO (lo ya cobrado) + línea del saldo con el método elegido")
        void anticipoParcial() {
            int o = listaConPantallaYMica();
            service.registrarAnticipo(o, anticipo("500", 1), "abigail");

            EntregaRequestDto dto = entrega(3);
            dto.setFolioOperacion("SPEI-88");
            service.entregar(o, dto, "abigail");

            VentaRequestDto v = ventaGenerada();
            assertThat(v.getMetodoPago()).isEqualTo((byte) 4);
            assertThat(v.getPagos()).hasSize(2);
            assertThat(v.getPagos().get(0).getMetodoPago()).isEqualTo((byte) 6);
            assertThat(v.getPagos().get(0).getMonto()).isEqualByComparingTo("500");
            assertThat(v.getPagos().get(1).getMetodoPago()).isEqualTo((byte) 3);
            assertThat(v.getPagos().get(1).getMonto()).isEqualByComparingTo("880");          // 1380 - 500
            assertThat(v.getPagos().get(1).getFolioOperacion()).isEqualTo("SPEI-88");
        }

        @Test
        @DisplayName("si el anticipo cubre todo el total: una sola línea ANTICIPO y no hace falta indicar cómo paga")
        void anticipoCubreTodo() {
            int o = listaConPantallaYMica();
            service.registrarAnticipo(o, anticipo("1380", 2), "abigail");

            OrdenServicioResponseDto r = service.entregar(o, entrega(null), "abigail");

            VentaRequestDto v = ventaGenerada();
            assertThat(v.getMetodoPago()).isEqualTo((byte) 4);
            assertThat(v.getPagos()).hasSize(1);
            assertThat(v.getPagos().get(0).getMetodoPago()).isEqualTo((byte) 6);
            assertThat(r.getSaldo()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("anticipos mayores al total: 409, no se genera la venta")
        void anticiposDeMas() {
            int o = listaConPantallaYMica();
            service.registrarAnticipo(o, anticipo("2000", 1), "abigail");

            assertThatThrownBy(() -> service.entregar(o, entrega(1), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("superan el total");
            verify(ventaService, never()).crearDesdeOrdenServicio(any(), anyInt());
        }

        @Test
        @DisplayName("lo que entrega el cliente (para el cambio) pasa a la venta cuando no hay anticipos")
        void montoRecibido() {
            int o = listaConPantallaYMica();
            EntregaRequestDto dto = entrega(1);
            dto.setMontoRecibido(new BigDecimal("1500"));

            service.entregar(o, dto, "abigail");

            assertThat(ventaGenerada().getMontoAbonado()).isEqualByComparingTo("1500");
        }

        @Test
        @DisplayName("si ningún renglón define garantía, la orden queda sin garantía")
        void sinGarantia() {
            mServicio.setDiasGarantia(null);
            int o = orden(linea("SRV-000001", null, null, null));
            hastaLista(o);

            OrdenServicioResponseDto r = service.entregar(o, entrega(1), "abigail");

            assertThat(r.getDiasGarantia()).isNull();
            assertThat(r.getFechaGarantiaHasta()).isNull();
        }

        @Test
        @DisplayName("solo se entrega una orden Lista (409); y solo el personal de su tienda (403)")
        void estadoYPermisos() {
            int enReparacion = orden(linea("SRV-000001", null, null, null));
            service.iniciar(enReparacion, "juan.perez");
            assertEstado(HttpStatus.CONFLICT, () -> service.entregar(enReparacion, entrega(1), "abigail"));

            int lista = orden(linea("SRV-000001", null, null, null));
            hastaLista(lista);
            assertEstado(HttpStatus.FORBIDDEN, () -> service.entregar(lista, entrega(1), "guillermo"));
            service.entregar(lista, entrega(1), "abigail");
            assertEstado(HttpStatus.CONFLICT, () -> service.entregar(lista, entrega(1), "abigail"));   // ya entregada
        }
    }

    // ── Cancelar ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelar")
    class Cancelar {

        private CancelarOrdenRequestDto motivo(Boolean devolver) {
            CancelarOrdenRequestDto c = new CancelarOrdenRequestDto();
            c.setMotivo("El cliente ya no quiere el arreglo");
            c.setDevolverAnticipo(devolver);
            return c;
        }

        @Test
        @DisplayName("sin devolver: queda Cancelada con su motivo y el anticipo se retiene")
        void sinDevolver() {
            int o = orden();
            service.registrarAnticipo(o, anticipo("300", 1), "abigail");

            OrdenServicioResponseDto r = service.cancelar(o, motivo(null), "abigail");

            assertThat(r.getEstadoDisplay()).isEqualTo("Cancelada");
            assertThat(r.getMotivoCancelacion()).isEqualTo("El cliente ya no quiere el arreglo");
            verify(movimientoCajaService, never()).registrarDevolucionAnticipo(any(), any(), any());
        }

        @Test
        @DisplayName("devolviendo el anticipo: solo los anticipos en efectivo salen de la caja")
        void devolviendo() {
            int o = orden();
            service.registrarAnticipo(o, anticipo("300", 1), "abigail");
            service.registrarAnticipo(o, anticipo("200", 1), "abigail");
            service.registrarAnticipo(o, anticipo("100", 2), "abigail");   // tarjeta: se reembolsa por fuera

            OrdenServicioResponseDto r = service.cancelar(o, motivo(true), "abigail");

            verify(movimientoCajaService, times(2)).registrarDevolucionAnticipo(any(OrdenServicio.class), any(OrdenServicioAnticipo.class), eq(encZocalo));
            assertThat(r.getHistorial().get(r.getHistorial().size() - 1).getComentario()).contains("Anticipo devuelto en efectivo: $500");
        }

        @Test
        @DisplayName("una orden entregada o ya cancelada no se cancela (409)")
        void noSePuede() {
            int entregada = orden(linea("SRV-000001", null, null, null));
            hastaLista(entregada);
            service.entregar(entregada, entrega(1), "abigail");
            int cancelada = orden();
            service.cancelar(cancelada, motivo(null), "abigail");

            assertEstado(HttpStatus.CONFLICT, () -> service.cancelar(entregada, motivo(null), "abigail"));
            assertEstado(HttpStatus.CONFLICT, () -> service.cancelar(cancelada, motivo(null), "abigail"));
            assertEstado(HttpStatus.NOT_FOUND, () -> service.cancelar(99999, motivo(null), "abigail"));
        }

        @Test
        @DisplayName("una orden cancelada ya no acepta anticipos ni entrega (409)")
        void canceladaCerrada() {
            int o = orden(linea("SRV-000001", null, null, null));
            service.cancelar(o, motivo(null), "abigail");

            assertEstado(HttpStatus.CONFLICT, () -> service.registrarAnticipo(o, anticipo("100", 1), "abigail"));
            assertEstado(HttpStatus.CONFLICT, () -> service.entregar(o, entrega(1), "abigail"));
        }
    }

    // ── Consulta pública del cliente ─────────────────────────────────────────

    @Nested
    @DisplayName("consulta pública por folio")
    class Publica {

        /** Una orden con IMEI, técnico, renglones, anticipo y diagnóstico: mucha información interna que no debe salir. */
        private int ordenConDatosInternos() {
            OrdenServicioRequestDto dto = recepcion();
            dto.setImei("352999000000001");
            dto.setIdTecnico(20);
            dto.setLineas(List.of(linea("SRV-000001", null, null, null)));
            dto.setAnticipo(anticipo("500", 1));
            int o = service.crear(dto, "root").getIdorden();
            OrdenActualizarDto diag = new OrdenActualizarDto();
            diag.setDiagnostico("Display y digitalizador dañados");
            service.iniciar(o, "juan.perez");
            service.actualizar(o, diag, "juan.perez");
            return o;
        }

        @Test
        @DisplayName("folio + últimos 4 dígitos del teléfono: equipo, IMEI enmascarado, estado con su mensaje y solo los cambios de estado")
        void consulta() {
            int o = ordenConDatosInternos();
            service.marcarLista(o, "juan.perez");

            OrdenServicioPublicaResponseDto r = service.consultaPublica("os-000001", "0001");

            assertThat(r.getFolio()).isEqualTo("OS-000001");
            assertThat(r.getEquipo()).isEqualTo("Apple iPhone 13");
            assertThat(r.getImei()).isEqualTo("•••••••••••0001");
            assertThat(r.getSucursal()).isEqualTo("Zocalo");
            assertThat(r.getEstado()).isEqualTo("Lista para entrega");
            assertThat(r.getMensaje()).isEqualTo("Tu equipo está listo. Pasa a recogerlo a la sucursal.");
            // la bitácora interna tiene varias anotaciones en «Recibida» y en «En reparación»; aquí solo se ven los cambios
            assertThat(r.getAvance()).extracting(OrdenServicioPublicaResponseDto.PasoDto::getEstado)
                    .containsExactly("Recibida", "En reparación", "Lista para entrega");
        }

        @Test
        @DisplayName("nunca expone técnico, importes, diagnóstico, falla, datos del cliente ni anotaciones de la bitácora")
        void sinDatosInternos() {
            ordenConDatosInternos();

            // sin las fechas: sus fracciones de segundo pueden contener por casualidad "500" o "1200"
            String salida = service.consultaPublica("OS-000001", "0001").toString()
                    .replaceAll("\\d{4}-\\d{2}-\\d{2}(T[0-9:.]+)?", "");

            assertThat(salida).doesNotContain("JUAN.PEREZ", "ABIGAIL", "Sofia", "7571200001", "Pantalla estrellada",
                    "digitalizador", "500", "1200", "Anticipo", "Técnico asignado", "Diagnóstico");
        }

        @Test
        @DisplayName("acepta el teléfono con formato o completo; solo compara los últimos 4 dígitos")
        void formatoDelTelefono() {
            orden();

            assertThat(service.consultaPublica("OS-000001", "757-120-0001").getFolio()).isEqualTo("OS-000001");
            assertThat(service.consultaPublica("OS-000001", "7571200001").getFolio()).isEqualTo("OS-000001");
        }

        @Test
        @DisplayName("teléfono que no coincide, folio inexistente, teléfono incompleto o cliente sin teléfono: siempre el mismo 404")
        void noRevelaNada() {
            orden();
            when(clienteRepository.findById(8)).thenReturn(Optional.of(Cliente.builder().idcliente(8).nombreCompleto("Sin Telefono").build()));
            OrdenServicioRequestDto sinTelefono = recepcion();
            sinTelefono.setIdcliente(8);
            service.crear(sinTelefono, "abigail");                       // OS-000002: su cliente no tiene teléfono

            Set<String> mensajes = new HashSet<>();
            int intentos = 0;
            for (String[] i : new String[][]{{"OS-000001", "9999"}, {"OS-000777", "0001"}, {"OS-000001", "01"}, {"OS-000001", null}, {"OS-000002", "0000"}}) {
                try {
                    service.consultaPublica(i[0], i[1]);
                } catch (ResponseStatusException e) {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    mensajes.add(e.getReason());
                    intentos++;
                }
            }

            assertThat(intentos).isEqualTo(5);
            assertThat(mensajes).hasSize(1);                             // los cinco casos dan exactamente el mismo mensaje
        }

        @Test
        @DisplayName("la garantía del trabajo solo se muestra una vez entregada; cancelada muestra su mensaje")
        void garantiaYCancelada() {
            int o = orden(linea("SRV-000001", null, null, null));
            hastaLista(o);
            OrdenServicioPublicaResponseDto lista = service.consultaPublica("OS-000001", "0001");
            assertThat(lista.getDiasGarantia()).isNull();

            service.entregar(o, entrega(1), "abigail");
            OrdenServicioPublicaResponseDto entregada = service.consultaPublica("OS-000001", "0001");
            assertThat(entregada.getEstado()).isEqualTo("Entregada");
            assertThat(entregada.getDiasGarantia()).isEqualTo(30);
            assertThat(entregada.getGarantiaHasta()).isEqualTo(LocalDate.now().plusDays(30));
            assertThat(entregada.getFechaEntrega()).isNotNull();

            int c = orden();
            CancelarOrdenRequestDto cancelar = new CancelarOrdenRequestDto();
            cancelar.setMotivo("El cliente ya no la quiere");
            service.cancelar(c, cancelar, "abigail");
            assertThat(service.consultaPublica("OS-000002", "0001").getMensaje()).contains("cancelada");
        }
    }
}
