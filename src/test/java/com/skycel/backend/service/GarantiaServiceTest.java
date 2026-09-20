package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.garantia.*;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Pruebas de GarantiaService sin Spring ni base de datos (repositorios simulados en memoria). */
@ExtendWith(MockitoExtension.class)
class GarantiaServiceTest {

    @Mock private GarantiaRepository          garantiaRepository;
    @Mock private GarantiaHistorialRepository historialRepository;
    @Mock private VentaRepository             ventaRepository;
    @Mock private VentaDetalleRepository      ventaDetalleRepository;
    @Mock private ProductoRepository          productoRepository;
    @Mock private ProductoImeiRepository      productoImeiRepository;
    @Mock private TiendaRepository            tiendaRepository;
    @Mock private UsuarioRepository           usuarioRepository;
    @Mock private CategoriaFolioRepository    categoriaFolioRepository;

    @InjectMocks
    private GarantiaService service;

    private final Map<Integer, Garantia> garantias = new HashMap<>();
    private final List<GarantiaHistorial> historial = new ArrayList<>();
    private final Map<Integer, Venta> ventas = new HashMap<>();
    private final Map<Integer, List<VentaDetalle>> lineas = new HashMap<>();
    private int id = 100;
    private long folio = 0;

    private Tienda almacen, zocalo, corpo;
    private Usuario root, admin, encZocalo, vendZocalo, encCorpo, tecnico;
    private Cliente sofia;
    private Proveedor proveedor;
    private ProductoMaster mCelular, mAccesorio, mSinGarantia, mServicio;
    private Producto pCelular, pAccesorio, pSinGarantia, pServicio;
    private Venta venta;                                  // venta 500: en Zócalo, hace 10 días, con cliente

    private static final String IMEI = "350000000000011";

    @BeforeEach
    void setUp() {
        almacen = Tienda.builder().codti(1).nombre("Almacen").build();
        zocalo  = Tienda.builder().codti(2).nombre("Zocalo").build();
        corpo   = Tienda.builder().codti(3).nombre("Corpo").build();
        root       = usuario(1, "root", Rol.ROOT, almacen);
        admin      = usuario(2, "gerente", Rol.ADMIN, almacen);
        encZocalo  = usuario(4, "abigail", Rol.ENCARGADO_TIENDA, zocalo);
        vendZocalo = usuario(9, "yamilet", Rol.VENDEDOR, zocalo);
        encCorpo   = usuario(5, "guillermo", Rol.ENCARGADO_TIENDA, corpo);
        tecnico    = usuario(20, "juan.perez", Rol.TECNICO, zocalo);
        sofia = Cliente.builder().idcliente(7).nombreCompleto("Sofia Ramirez").telefono("7571200001").build();
        proveedor = Proveedor.builder().idproveedor((short) 2).nombreCorto("Mayorista Movil MX").build();

        mCelular     = ProductoMaster.builder().idprodmaster(1).tipo(TipoProducto.CELULAR).nombreBase("iPhone 16 Pro").diasGarantia(365).build();
        mAccesorio   = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.ACCESORIO).nombreBase("Funda Silicon").diasGarantia(30).build();
        mSinGarantia = ProductoMaster.builder().idprodmaster(3).tipo(TipoProducto.ACCESORIO).nombreBase("Cable Micro USB").build();
        mServicio    = ProductoMaster.builder().idprodmaster(4).tipo(TipoProducto.SERVICIO).nombreBase("Cambio de Pantalla").diasGarantia(30).build();
        pCelular     = producto(10, "CEL-000002", mCelular);
        pAccesorio   = producto(11, "ACC-000001", mAccesorio);
        pSinGarantia = producto(12, "CAB-000001", mSinGarantia);
        pServicio    = producto(13, "SRV-000001", mServicio);
        pCelular.setProveedor(proveedor);

        venta = venta(500, zocalo, sofia, 1, 10);
        agregarLinea(500, mCelular, "CEL-000002", IMEI);
        agregarLinea(500, mAccesorio, "ACC-000001", null);
        agregarLinea(500, mSinGarantia, "CAB-000001", null);
        agregarLinea(500, mServicio, "SRV-000001", null);

        for (Tienda t : List.of(almacen, zocalo, corpo)) lenient().when(tiendaRepository.findById(t.getCodti())).thenReturn(Optional.of(t));
        for (Usuario u : List.of(root, admin, encZocalo, vendZocalo, encCorpo, tecnico)) lenient().when(usuarioRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));
        lenient().when(categoriaFolioRepository.obtenerUltimoFolioGenerado((short) -2)).thenAnswer(inv -> ++folio);
        lenient().when(ventaRepository.findById(anyInt())).thenAnswer(inv -> Optional.ofNullable(ventas.get((Integer) inv.getArgument(0))));
        lenient().when(ventaDetalleRepository.findByVenta_Idventa(anyInt())).thenAnswer(inv -> lineas.getOrDefault((Integer) inv.getArgument(0), List.of()));
        lenient().when(productoImeiRepository.findByImei(IMEI)).thenReturn(Optional.of(ProductoImei.builder().imei(IMEI).producto(pCelular).build()));
        lenient().when(productoRepository.findByCodproAndTienda_Codti(anyString(), anyInt())).thenAnswer(inv ->
                List.of(pAccesorio, pSinGarantia, pServicio, pCelular).stream().filter(p -> p.getCodpro().equals(inv.getArgument(0))).findFirst());

        lenient().when(garantiaRepository.save(any(Garantia.class))).thenAnswer(inv -> {
            Garantia g = inv.getArgument(0);
            if (g.getIdgarantia() == null) { g.setIdgarantia(id++); g.setFechaIngreso(LocalDateTime.now()); }
            garantias.put(g.getIdgarantia(), g);
            return g;
        });
        lenient().when(garantiaRepository.findById(anyInt())).thenAnswer(inv -> Optional.ofNullable(garantias.get((Integer) inv.getArgument(0))));
        lenient().when(garantiaRepository.findByFolioSeguimiento(anyString())).thenAnswer(inv ->
                garantias.values().stream().filter(g -> g.getFolioSeguimiento().equals(inv.getArgument(0))).findFirst());
        lenient().when(garantiaRepository.findByVenta_IdventaAndProducto_Idproducto(anyInt(), anyInt())).thenAnswer(inv ->
                garantias.values().stream().filter(g -> g.getVenta().getIdventa().equals(inv.getArgument(0))
                        && g.getProducto().getIdproducto().equals(inv.getArgument(1))).collect(Collectors.toList()));
        lenient().when(historialRepository.save(any(GarantiaHistorial.class))).thenAnswer(inv -> {
            GarantiaHistorial h = inv.getArgument(0);
            h.setFechaMovimiento(LocalDateTime.now());
            historial.add(h);
            return h;
        });
        lenient().when(historialRepository.findByGarantia_IdgarantiaOrderByIdhistorial(anyInt())).thenAnswer(inv ->
                historial.stream().filter(h -> h.getGarantia().getIdgarantia().equals(inv.getArgument(0))).collect(Collectors.toList()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Usuario usuario(int idu, String username, Rol rol, Tienda t) {
        return Usuario.builder().idusuario(idu).username(username).nombreCompleto(username.toUpperCase()).rol(rol).tienda(t).activo(true).build();
    }

    private Producto producto(int idp, String codpro, ProductoMaster m) {
        return Producto.builder().idproducto(idp).codpro(codpro).tienda(zocalo).productoMaster(m).build();
    }

    private Venta venta(int idv, Tienda t, Cliente c, int estado, int diasAtras) {
        Venta v = Venta.builder().idventa(idv).tienda(t).cliente(c).estado((byte) estado).fechaVenta(LocalDateTime.now().minusDays(diasAtras)).build();
        ventas.put(idv, v);
        return v;
    }

    private void agregarLinea(int idv, ProductoMaster m, String codpro, String imei) {
        lineas.computeIfAbsent(idv, k -> new ArrayList<>()).add(VentaDetalle.builder().productoMaster(m).codpro(codpro).imei(imei).build());
    }

    private GarantiaRequestDto reclamo(int idventa, String imei, String codpro) {
        GarantiaRequestDto d = new GarantiaRequestDto();
        d.setIdventa(idventa);
        d.setImei(imei);
        d.setCodpro(codpro);
        d.setFallaReportada("No enciende");
        return d;
    }

    private AvanceGarantiaRequestDto paso(int estado, String comentario) {
        AvanceGarantiaRequestDto a = new AvanceGarantiaRequestDto();
        a.setEstado((byte) estado);
        a.setComentario(comentario);
        return a;
    }

    /** Abre una garantía del equipo de la venta 500 recibida en Zócalo. */
    private int abrir() {
        return service.crear(reclamo(500, IMEI, null), "abigail").getIdgarantia();
    }

    private void ir(int idg, String usuario, int... estados) {
        for (int e : estados) service.avanzar(idg, paso(e, e >= 6 && e <= 8 ? "Resolución del proveedor" : null), usuario);
    }

    private void assertEstado(HttpStatus esperado, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    // ── Recibir un reclamo ───────────────────────────────────────────────────

    @Nested
    @DisplayName("recibir un reclamo")
    class Recibir {

        @Test
        @DisplayName("un equipo por su IMEI: folio, contacto del cliente, proveedor del producto, plazo de 30 días hábiles y bitácora")
        void equipo() {
            GarantiaResponseDto r = service.crear(reclamo(500, IMEI, null), "abigail");

            assertThat(r.getFolio()).isEqualTo("GAR-000001");
            assertThat(r.getEstado()).isEqualTo((byte) 1);
            assertThat(r.getEstadoDisplay()).isEqualTo("Recibido en sucursal");
            assertThat(r.getCodti()).isEqualTo(2);
            assertThat(r.getNombreContacto()).isEqualTo("Sofia Ramirez");
            assertThat(r.getTelefonoContacto()).isEqualTo("7571200001");
            assertThat(r.getProveedor()).isEqualTo("Mayorista Movil MX");
            assertThat(r.getImei()).isEqualTo(IMEI);
            assertThat(r.getNombreProducto()).isEqualTo("iPhone 16 Pro");
            assertThat(r.getFechaLimiteSolucion()).isEqualTo(GarantiaService.sumarDiasHabiles(LocalDate.now(), 30));
            assertThat(r.getGarantiaVigenteHasta()).isEqualTo(LocalDate.now().minusDays(10).plusDays(365));
            assertThat(r.getVencida()).isFalse();
            assertThat(r.getHistorial()).hasSize(1);
            assertThat(r.getHistorial().get(0).getComentario()).startsWith("Producto recibido en Zocalo");
        }

        @Test
        @DisplayName("un accesorio por su código de producto; los folios son consecutivos")
        void accesorioYFolios() {
            assertThat(service.crear(reclamo(500, null, "ACC-000001"), "abigail").getFolio()).isEqualTo("GAR-000001");
            GarantiaResponseDto r = service.crear(reclamo(500, IMEI, null), "abigail");

            assertThat(r.getFolio()).isEqualTo("GAR-000002");
        }

        @Test
        @DisplayName("si la venta no tiene cliente hay que indicar el contacto; con contacto se acepta y prevalece el indicado")
        void contacto() {
            venta(501, zocalo, null, 1, 5);
            agregarLinea(501, mAccesorio, "ACC-000001", null);

            assertThatThrownBy(() -> service.crear(reclamo(501, null, "ACC-000001"), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nombreContacto y telefonoContacto");

            GarantiaRequestDto dto = reclamo(501, null, "ACC-000001");
            dto.setNombreContacto("  Pedro Mostrador ");
            dto.setTelefonoContacto("757 555 1234");
            GarantiaResponseDto r = service.crear(dto, "abigail");
            assertThat(r.getNombreContacto()).isEqualTo("Pedro Mostrador");

            GarantiaRequestDto otraPersona = reclamo(500, null, "ACC-000001");
            otraPersona.setNombreContacto("Hermana del cliente");
            otraPersona.setTelefonoContacto("7579990000");
            assertThat(service.crear(otraPersona, "abigail").getTelefonoContacto()).isEqualTo("7579990000");
        }

        @Test
        @DisplayName("teléfono de contacto con menos de 4 dígitos: 400")
        void telefonoCorto() {
            venta(501, zocalo, null, 1, 5);
            agregarLinea(501, mAccesorio, "ACC-000001", null);
            GarantiaRequestDto dto = reclamo(501, null, "ACC-000001");
            dto.setNombreContacto("Pedro");
            dto.setTelefonoContacto("12");

            assertThatThrownBy(() -> service.crear(dto, "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("al menos 4 dígitos");
        }

        @Test
        @DisplayName("el producto debe aparecer en la venta: IMEI o código ajenos, o ninguno de los dos: 400")
        void productoNoEnLaVenta() {
            assertThatThrownBy(() -> service.crear(reclamo(500, "999999999999999", null), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no aparece en la venta");
            assertThatThrownBy(() -> service.crear(reclamo(500, null, "NO-EXISTE"), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no aparece en la venta");
            assertThatThrownBy(() -> service.crear(reclamo(500, null, null), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Indique el imei");
        }

        @Test
        @DisplayName("un servicio, un producto sin garantía definida, una garantía vencida o una venta cancelada no proceden")
        void noProcede() {
            assertThatThrownBy(() -> service.crear(reclamo(500, null, "SRV-000001"), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Un servicio no entra a garantía");
            assertEstado(HttpStatus.CONFLICT, () -> service.crear(reclamo(500, null, "CAB-000001"), "abigail"));

            venta(502, zocalo, sofia, 1, 400);                   // vendida hace 400 días: los 365 ya vencieron
            agregarLinea(502, mCelular, "CEL-000002", IMEI);
            assertThatThrownBy(() -> service.crear(reclamo(502, IMEI, null), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("venció el " + LocalDate.now().minusDays(400).plusDays(365));

            venta(503, zocalo, sofia, 2, 3);                     // cancelada
            agregarLinea(503, mAccesorio, "ACC-000001", null);
            assertThatThrownBy(() -> service.crear(reclamo(503, null, "ACC-000001"), "abigail"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cancelada");
            assertEstado(HttpStatus.NOT_FOUND, () -> service.crear(reclamo(9999, IMEI, null), "abigail"));
        }

        @Test
        @DisplayName("el último día de la garantía todavía procede")
        void ultimoDia() {
            venta(504, zocalo, sofia, 1, 30);                    // accesorio de 30 días, vendido hace 30 días
            agregarLinea(504, mAccesorio, "ACC-000001", null);

            assertThat(service.crear(reclamo(504, null, "ACC-000001"), "abigail").getFolio()).isNotNull();
        }

        @Test
        @DisplayName("no se abren dos reclamos a la vez sobre el mismo equipo (409); ya entregado, sí se puede abrir otro")
        void duplicados() {
            int primera = abrir();
            assertEstado(HttpStatus.CONFLICT, () -> service.crear(reclamo(500, IMEI, null), "abigail"));

            ir(primera, "gerente", 2, 3, 4, 5, 6, 9, 10, 11);
            assertThat(service.crear(reclamo(500, IMEI, null), "abigail").getFolio()).isEqualTo("GAR-000002");
        }

        @Test
        @DisplayName("permisos: un encargado no recibe en otra sucursal (403), un técnico no recibe (403); un administrador, donde sea")
        void permisos() {
            GarantiaRequestDto enZocalo = reclamo(500, IMEI, null);
            enZocalo.setCodti(2);
            assertEstado(HttpStatus.FORBIDDEN, () -> service.crear(enZocalo, "guillermo"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.crear(reclamo(500, IMEI, null), "juan.perez"));

            assertThat(service.crear(reclamo(500, null, "ACC-000001"), "yamilet").getCodti()).isEqualTo(2);   // un vendedor sí
            GarantiaRequestDto enCorpo = reclamo(500, IMEI, null);
            enCorpo.setCodti(3);
            assertThat(service.crear(enCorpo, "root").getCodti()).isEqualTo(3);
        }

        @Test
        @DisplayName("la garantía se recibe en la sucursal aunque el equipo se haya vendido en otra")
        void otraSucursalQueLaVendio() {
            GarantiaRequestDto dto = reclamo(500, IMEI, null);
            dto.setCodti(3);

            GarantiaResponseDto r = service.crear(dto, "guillermo");

            assertThat(r.getCodti()).isEqualTo(3);
            assertThat(r.getIdventa()).isEqualTo(500);
        }
    }

    // ── Elegibilidad y plazos ────────────────────────────────────────────────

    @Nested
    @DisplayName("elegibilidad y días hábiles")
    class Elegibilidad {

        @Test
        @DisplayName("dice si procede y cuántos días le quedan; si no procede, por qué")
        void elegibilidad() {
            ElegibilidadGarantiaResponseDto si = service.elegibilidad(500, null, "ACC-000001", "abigail");
            assertThat(si.getElegible()).isTrue();
            assertThat(si.getDiasGarantia()).isEqualTo(30);
            assertThat(si.getDiasRestantes()).isEqualTo(20);           // vendida hace 10 de 30 días
            assertThat(si.getNombreProducto()).isEqualTo("Funda Silicon");

            ElegibilidadGarantiaResponseDto no = service.elegibilidad(500, null, "CAB-000001", "abigail");
            assertThat(no.getElegible()).isFalse();
            assertThat(no.getMotivo()).contains("no tiene garantía definida");
        }

        @Test
        @DisplayName("sumar días hábiles salta sábados y domingos")
        void diasHabiles() {
            LocalDate viernes = LocalDate.of(2026, 9, 18), sabado = LocalDate.of(2026, 9, 19), lunes = LocalDate.of(2026, 9, 21);

            assertThat(GarantiaService.sumarDiasHabiles(viernes, 1)).isEqualTo(lunes);
            assertThat(GarantiaService.sumarDiasHabiles(sabado, 1)).isEqualTo(lunes);
            assertThat(GarantiaService.sumarDiasHabiles(viernes, 5)).isEqualTo(LocalDate.of(2026, 9, 25));
            assertThat(GarantiaService.sumarDiasHabiles(lunes, 30)).isEqualTo(lunes.plusDays(42));   // 6 semanas exactas
            assertThat(GarantiaService.sumarDiasHabiles(lunes, 0)).isEqualTo(lunes);
        }
    }

    // ── Seguimiento ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("seguimiento y estados")
    class Seguimiento {

        @Test
        @DisplayName("el camino completo: sucursal → bodega → proveedor → reparado → retorno → listo → entregado, con su bitácora")
        void caminoCompleto() {
            int g = abrir();

            GarantiaResponseDto r = null;
            for (int e : new int[]{2, 3, 4, 5, 6, 9, 10, 11}) {
                r = service.avanzar(g, paso(e, e == 6 ? "Cambio de placa" : null), "gerente");
                assertThat(r.getEstado()).isEqualTo((byte) e);
            }

            assertThat(r.getEstadoDisplay()).isEqualTo("Entregado");
            assertThat(r.getDiasRestantes()).isNull();                        // ya no corre el plazo
            assertThat(r.getVencida()).isFalse();
            assertThat(r.getHistorial()).extracting(GarantiaResponseDto.HistorialDto::getEstado)
                    .containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 9, (byte) 10, (byte) 11);
            assertThat(r.getHistorial().get(5).getComentario()).isEqualTo("Cambio de placa");
            assertThat(r.getHistorial().get(5).getNombreUsuario()).isEqualTo("GERENTE");
        }

        @Test
        @DisplayName("un paso que no corresponde (1→3) o uno después de entregado: 409, indicando a dónde se puede ir")
        void transicionInvalida() {
            int g = abrir();

            assertThatThrownBy(() -> service.avanzar(g, paso(3, null), "gerente"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).contains("Desde ahí se puede ir a: 2 En tránsito a bodega, 8 Rechazado");
                    });
            ir(g, "gerente", 2, 3, 4, 5, 6, 9, 10, 11);
            assertThatThrownBy(() -> service.avanzar(g, paso(2, null), "gerente"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya fue entregada");
        }

        @Test
        @DisplayName("resolver (reparado, cambio o rechazo) exige comentario")
        void comentarioObligatorio() {
            int g = abrir();
            ir(g, "gerente", 2, 3, 4, 5);

            for (int resolucion : new int[]{6, 7, 8}) {
                assertThatThrownBy(() -> service.avanzar(g, paso(resolucion, "  "), "gerente"))
                        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("comentario es obligatorio");
            }
        }

        @Test
        @DisplayName("cambio físico: se registra el IMEI del equipo nuevo; en otro estado o con formato inválido, 400")
        void cambioFisico() {
            int g = abrir();
            ir(g, "gerente", 2, 3, 4, 5);
            AvanceGarantiaRequestDto invalido = paso(7, "Cambio autorizado");
            invalido.setImeiReemplazo("12 34");
            assertThatThrownBy(() -> service.avanzar(g, invalido, "gerente")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("5 y 20");

            AvanceGarantiaRequestDto ok = paso(7, "Cambio autorizado");
            ok.setImeiReemplazo("350999000000077");
            GarantiaResponseDto r = service.avanzar(g, ok, "gerente");
            assertThat(r.getImeiReemplazo()).isEqualTo("350999000000077");

            int g2 = service.crear(reclamo(500, null, "ACC-000001"), "abigail").getIdgarantia();
            AvanceGarantiaRequestDto fuera = paso(2, null);
            fuera.setImeiReemplazo("350999000000088");
            assertThatThrownBy(() -> service.avanzar(g2, fuera, "abigail")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("solo aplica en un cambio físico");
        }

        @Test
        @DisplayName("la sucursal registra sus pasos (enviar, rechazar, recibir de regreso, entregar); bodega y proveedor, no (403)")
        void permisosPorPaso() {
            int g = abrir();

            service.avanzar(g, paso(2, null), "abigail");                                           // 1→2: sucursal
            assertEstado(HttpStatus.FORBIDDEN, () -> service.avanzar(g, paso(3, null), "abigail")); // 2→3: bodega
            ir(g, "gerente", 3, 4);
            assertEstado(HttpStatus.FORBIDDEN, () -> service.avanzar(g, paso(5, null), "yamilet")); // 4→5: proveedor
            ir(g, "gerente", 5, 6, 9);
            service.avanzar(g, paso(10, null), "yamilet");                                          // 9→10: llegó de regreso a la sucursal
            service.avanzar(g, paso(11, null), "yamilet");                                          // 10→11: entrega al cliente
            assertThat(service.obtener(g, "abigail").getEstadoDisplay()).isEqualTo("Entregado");
        }

        @Test
        @DisplayName("el encargado de otra sucursal no puede mover la garantía (403), ni un técnico")
        void otraSucursal() {
            int g = abrir();

            assertEstado(HttpStatus.FORBIDDEN, () -> service.avanzar(g, paso(2, null), "guillermo"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.avanzar(g, paso(2, null), "juan.perez"));
        }

        @Test
        @DisplayName("rechazo en la sucursal (nunca salió): pasa directo a Listo para entrega; no tiene viaje de retorno (409)")
        void rechazoEnSucursal() {
            int g = abrir();

            service.avanzar(g, paso(8, "Pantalla con golpe visible: no cubre la garantía"), "abigail");
            assertThatThrownBy(() -> service.avanzar(g, paso(9, null), "gerente"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nunca salió de la sucursal");
            service.avanzar(g, paso(10, null), "abigail");
            assertThat(service.avanzar(g, paso(11, null), "abigail").getEstadoDisplay()).isEqualTo("Entregado");
        }

        @Test
        @DisplayName("rechazo del proveedor (ya salió): primero regresa (9); no puede saltar a Listo para entrega (409)")
        void rechazoDelProveedor() {
            int g = abrir();
            ir(g, "gerente", 2, 3, 4, 5, 8);

            assertThatThrownBy(() -> service.avanzar(g, paso(10, null), "gerente"))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya salió de la sucursal");
            ir(g, "gerente", 9, 10);
            assertThat(service.obtener(g, "gerente").getEstadoDisplay()).isEqualTo("Listo para entrega");
        }

        @Test
        @DisplayName("una garantía que pasó su fecha límite sin entregarse aparece vencida, con días restantes negativos")
        void vencida() {
            int g = abrir();
            garantias.get(g).setFechaLimiteSolucion(LocalDate.now().minusDays(3));

            GarantiaResponseDto r = service.obtener(g, "abigail");

            assertThat(r.getVencida()).isTrue();
            assertThat(r.getDiasRestantes()).isEqualTo(-3);
            ir(g, "gerente", 2, 3, 4, 5, 6, 9, 10, 11);
            assertThat(service.obtener(g, "abigail").getVencida()).isFalse();   // ya entregada
        }
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("consultas")
    class Consultas {

        @Test
        @DisplayName("obtener: la sucursal que la recibió y los administradores; otra sucursal, no (403); por folio sin importar mayúsculas")
        void obtener() {
            int g = abrir();

            assertThat(service.obtener(g, "abigail").getFolio()).isEqualTo("GAR-000001");
            assertThat(service.obtener(g, "gerente").getFolio()).isEqualTo("GAR-000001");
            assertEstado(HttpStatus.FORBIDDEN, () -> service.obtener(g, "guillermo"));
            assertThat(service.obtenerPorFolio(" gar-000001 ", "abigail").getIdgarantia()).isEqualTo(g);
            assertEstado(HttpStatus.NOT_FOUND, () -> service.obtenerPorFolio("GAR-999999", "abigail"));
            assertEstado(HttpStatus.NOT_FOUND, () -> service.obtener(9999, "abigail"));
        }

        @Test
        @DisplayName("listar: un encargado solo ve su sucursal (aunque no la indique); un administrador ve todas")
        void listar() {
            when(garantiaRepository.buscar(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(List.of());
            ArgumentCaptor<Integer> codti = ArgumentCaptor.forClass(Integer.class);

            service.listar(null, null, true, false, "abigail");
            verify(garantiaRepository).buscar(codti.capture(), isNull(), eq(true), eq(false), any(LocalDate.class));
            assertThat(codti.getValue()).isEqualTo(2);

            assertEstado(HttpStatus.FORBIDDEN, () -> service.listar(3, null, false, false, "abigail"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.listar(null, null, false, false, "juan.perez"));

            reset(garantiaRepository);
            when(garantiaRepository.buscar(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(List.of());
            service.listar(null, null, false, true, "gerente");
            verify(garantiaRepository).buscar(isNull(), isNull(), eq(false), eq(true), any(LocalDate.class));
        }
    }

    // ── Consulta pública del cliente ─────────────────────────────────────────

    @Nested
    @DisplayName("consulta pública por folio")
    class Publica {

        @Test
        @DisplayName("folio + últimos 4 dígitos del teléfono: devuelve el avance, con el IMEI enmascarado y sin datos personales")
        void consulta() {
            int g = abrir();
            ir(g, "gerente", 2, 3);

            GarantiaPublicaResponseDto r = service.consultaPublica("gar-000001", "0001");

            assertThat(r.getFolio()).isEqualTo("GAR-000001");
            assertThat(r.getProducto()).isEqualTo("iPhone 16 Pro");
            assertThat(r.getImei()).isEqualTo("•••••••••••0011");
            assertThat(r.getSucursal()).isEqualTo("Zocalo");
            assertThat(r.getEstado()).isEqualTo("Recibido en bodega");
            assertThat(r.getMensaje()).isEqualTo("Tu producto llegó a nuestra bodega.");
            assertThat(r.getAvance()).extracting(GarantiaPublicaResponseDto.PasoDto::getEstado)
                    .containsExactly("Recibido en sucursal", "En tránsito a bodega", "Recibido en bodega");
        }

        @Test
        @DisplayName("acepta el teléfono con formato o completo, y solo compara los últimos 4 dígitos")
        void formatoDelTelefono() {
            abrir();

            assertThat(service.consultaPublica("GAR-000001", "757-120-0001").getFolio()).isEqualTo("GAR-000001");
            assertThat(service.consultaPublica("GAR-000001", "7571200001").getFolio()).isEqualTo("GAR-000001");
        }

        @Test
        @DisplayName("teléfono que no coincide, folio inexistente o teléfono incompleto: la misma respuesta 404 (no revela si el folio existe)")
        void noRevelaNada() {
            abrir();
            String[] errores = new String[4];
            int i = 0;
            for (String[] intento : new String[][]{{"GAR-000001", "9999"}, {"GAR-000777", "0001"}, {"GAR-000001", "01"}, {"GAR-000001", null}}) {
                try {
                    service.consultaPublica(intento[0], intento[1]);
                } catch (ResponseStatusException e) {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    errores[i++] = e.getReason();
                }
            }

            assertThat(i).isEqualTo(4);
            assertThat(new HashSet<>(Arrays.asList(errores))).hasSize(1);  // los cuatro casos dan exactamente el mismo mensaje
        }

        @Test
        @DisplayName("nunca expone el nombre, el teléfono, los usuarios ni los comentarios internos")
        void sinDatosInternos() {
            int g = abrir();
            service.avanzar(g, paso(8, "Golpe visible: el cliente Sofia Ramirez pagó de más, tel 7571200001"), "abigail");

            GarantiaPublicaResponseDto r = service.consultaPublica("GAR-000001", "0001");

            assertThat(r.toString()).doesNotContain("Sofia", "7571200001", "ABIGAIL", "Golpe visible", "pagó de más");
            assertThat(r.getMensaje()).contains("no procedió");
        }
    }
}
