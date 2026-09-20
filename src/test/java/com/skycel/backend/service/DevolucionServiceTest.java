package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.devolucion.*;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Devoluciones (sin Spring ni base de datos): repositorios en memoria y colaboradores simulados. */
@ExtendWith(MockitoExtension.class)
class DevolucionServiceTest {

    @Mock private DevolucionRepository            devolucionRepository;
    @Mock private DevolucionDetalleRepository     detalleRepository;
    @Mock private DevolucionCambioLineaRepository cambioRepository;
    @Mock private VentaRepository                 ventaRepository;
    @Mock private VentaDetalleRepository          ventaDetalleRepository;
    @Mock private ProductoRepository              productoRepository;
    @Mock private ProductoMasterRepository        productoMasterRepository;
    @Mock private ProductoImeiRepository          productoImeiRepository;
    @Mock private CajaRepository                  cajaRepository;
    @Mock private UsuarioRepository               usuarioRepository;
    @Mock private CategoriaFolioRepository        categoriaFolioRepository;
    @Mock private CuentaPorCobrarRepository       cuentaPorCobrarRepository;
    @Mock private OrdenServicioRepository         ordenServicioRepository;
    @Mock private MovimientoCajaService           movimientoCajaService;
    @Mock private MovimientoInventarioService     movimientoInventarioService;
    @Mock private VentaService                    ventaService;
    @Mock private ConfiguracionNegocioService     configuracionNegocioService;

    @InjectMocks
    private DevolucionService service;

    private final Map<Integer, Devolucion> devoluciones = new HashMap<>();
    private final List<DevolucionDetalle> detalles = new ArrayList<>();
    private final List<DevolucionCambioLinea> cambios = new ArrayList<>();
    private int siguiente = 1;

    private Tienda zocalo, corpo;
    private Usuario vendedor, encargado, encargadoCorpo, root;
    private Caja caja;
    private Venta venta;
    private ProductoMaster mAccesorio, mCelular, mServicio;
    private VentaDetalle dAccesorio, dCelular, dServicio;
    private Producto pAccesorio, pCelular;
    private ProductoImei imei;

    @BeforeEach
    void setUp() {
        lenient().when(configuracionNegocioService.diasDevolucion()).thenReturn(15);
        zocalo = Tienda.builder().codti(2).nombre("Zocalo").build();
        corpo = Tienda.builder().codti(3).nombre("Corpo").build();
        vendedor = Usuario.builder().idusuario(9).username("yamilet").nombreCompleto("Yamilet").rol(Rol.VENDEDOR).tienda(zocalo).build();
        encargado = Usuario.builder().idusuario(4).username("abigail").nombreCompleto("Abigail").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        encargadoCorpo = Usuario.builder().idusuario(5).username("guillermo").nombreCompleto("Guillermo").rol(Rol.ENCARGADO_TIENDA).tienda(corpo).build();
        root = Usuario.builder().idusuario(1).username("root").nombreCompleto("Root").rol(Rol.ROOT).tienda(zocalo).build();
        for (Usuario u : List.of(vendedor, encargado, encargadoCorpo, root)) lenient().when(usuarioRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));

        caja = Caja.builder().idCaja(1).nombreCaja("Caja Zocalo").tienda(zocalo).build();
        Cliente cliente = Cliente.builder().idcliente(6).nombreCompleto("Sofia").build();
        venta = Venta.builder().idventa(50).tienda(zocalo).caja(caja).cliente(cliente).estado((byte) 1).metodoPago((byte) 1)
                .total(new BigDecimal("5330")).fechaVenta(LocalDateTime.now().minusDays(2)).build();
        lenient().when(ventaRepository.findById(50)).thenReturn(Optional.of(venta));
        lenient().when(ventaRepository.getReferenceById(anyInt())).thenAnswer(i -> Venta.builder().idventa(i.getArgument(0)).build());

        mAccesorio = ProductoMaster.builder().idprodmaster(2).tipo(TipoProducto.ACCESORIO).nombreBase("Funda").build();
        mCelular = ProductoMaster.builder().idprodmaster(1).tipo(TipoProducto.CELULAR).nombreBase("Galaxy A15").build();
        mServicio = ProductoMaster.builder().idprodmaster(3).tipo(TipoProducto.SERVICIO).nombreBase("Cambio de pantalla").build();
        dAccesorio = VentaDetalle.builder().iddetalleVenta(10).venta(venta).productoMaster(mAccesorio).codpro("FUN-000001").cantidad((short) 3)
                .precioUnitarioFinal(new BigDecimal("100")).esRegalo(false).build();
        dCelular = VentaDetalle.builder().iddetalleVenta(11).venta(venta).productoMaster(mCelular).codpro("CEL-000004").cantidad((short) 1)
                .precioUnitarioFinal(new BigDecimal("5000")).imei("350000000000011").esRegalo(false).build();
        dServicio = VentaDetalle.builder().iddetalleVenta(12).venta(venta).productoMaster(mServicio).codpro("SRV-000001").cantidad((short) 1)
                .precioUnitarioFinal(new BigDecimal("30")).esRegalo(false).build();
        lenient().when(ventaDetalleRepository.findByVenta_Idventa(50)).thenReturn(List.of(dAccesorio, dCelular, dServicio));

        pAccesorio = Producto.builder().idproducto(1).codpro("FUN-000001").tienda(zocalo).productoMaster(mAccesorio).stock(new BigDecimal("10")).build();
        pCelular = Producto.builder().idproducto(2).codpro("CEL-000004").tienda(zocalo).productoMaster(mCelular).stock(BigDecimal.ZERO).build();
        imei = ProductoImei.builder().imei("350000000000011").producto(pCelular).estado("VENDIDO").build();
        lenient().when(productoRepository.findByCodproAndTienda_Codti("FUN-000001", 2)).thenReturn(Optional.of(pAccesorio));
        lenient().when(productoImeiRepository.findByImei("350000000000011")).thenReturn(Optional.of(imei));

        lenient().when(cuentaPorCobrarRepository.findByVenta_Idventa(anyInt())).thenReturn(Optional.empty());
        lenient().when(ordenServicioRepository.existsByVenta_Idventa(anyInt())).thenReturn(false);
        lenient().when(detalleRepository.cantidadEnTramiteOProcesada(anyInt())).thenAnswer(inv -> detalles.stream()
                .filter(d -> d.getIddetalleVenta().equals(inv.getArgument(0)) && d.getDevolucion().getEstado() != DevolucionService.RECHAZADA)
                .mapToLong(DevolucionDetalle::getCantidad).sum());
        lenient().when(categoriaFolioRepository.obtenerUltimoFolioGenerado(anyShort())).thenReturn(1L);

        lenient().when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(inv -> {
            Devolucion d = inv.getArgument(0);
            if (d.getIddevolucion() == null) d.setIddevolucion(siguiente++);
            devoluciones.put(d.getIddevolucion(), d);
            return d;
        });
        lenient().when(devolucionRepository.findById(anyInt())).thenAnswer(inv -> Optional.ofNullable(devoluciones.get((Integer) inv.getArgument(0))));
        lenient().when(detalleRepository.save(any(DevolucionDetalle.class))).thenAnswer(inv -> { detalles.add(inv.getArgument(0)); return inv.getArgument(0); });
        lenient().when(cambioRepository.save(any(DevolucionCambioLinea.class))).thenAnswer(inv -> { cambios.add(inv.getArgument(0)); return inv.getArgument(0); });
        lenient().when(detalleRepository.findByDevolucion_IddevolucionOrderByIddetalle(anyInt())).thenAnswer(inv ->
                detalles.stream().filter(d -> d.getDevolucion().getIddevolucion().equals(inv.getArgument(0))).collect(Collectors.toList()));
        lenient().when(cambioRepository.findByDevolucion_IddevolucionOrderByIdlinea(anyInt())).thenAnswer(inv ->
                cambios.stream().filter(c -> c.getDevolucion().getIddevolucion().equals(inv.getArgument(0))).collect(Collectors.toList()));
    }

    private static short anyShort() { return org.mockito.ArgumentMatchers.anyShort(); }

    private DevolucionRequestDto.LineaDevolucionDto linea(int iddetalleVenta, int cantidad, boolean reingresa) {
        DevolucionRequestDto.LineaDevolucionDto l = new DevolucionRequestDto.LineaDevolucionDto();
        l.setIddetalleVenta(iddetalleVenta);
        l.setCantidad((short) cantidad);
        l.setReingresa(reingresa);
        return l;
    }

    private DevolucionRequestDto reembolso(DevolucionRequestDto.LineaDevolucionDto... lineas) {
        DevolucionRequestDto dto = new DevolucionRequestDto();
        dto.setIdventa(50);
        dto.setTipo((byte) 1);
        dto.setMotivo("No le gustó");
        dto.setLineas(List.of(lineas));
        return dto;
    }

    private DevolucionRequestDto solicitudCambio(String precio, DevolucionRequestDto.LineaDevolucionDto... lineas) {
        DevolucionRequestDto dto = reembolso(lineas);
        dto.setTipo((byte) 2);
        DevolucionRequestDto.LineaCambioDto c = new DevolucionRequestDto.LineaCambioDto();
        c.setIdprodmaster(2);
        c.setCodpro("FUN-000009");
        c.setCantidad((short) 1);
        c.setPrecioUnitarioFinal(new BigDecimal(precio));
        DevolucionRequestDto.CambioDto cd = new DevolucionRequestDto.CambioDto();
        cd.setLineas(List.of(c));
        cd.setMetodoPago((byte) 1);
        dto.setCambio(cd);
        lenient().when(productoMasterRepository.findById(2)).thenReturn(Optional.of(mAccesorio));
        return dto;
    }

    private void assertEstado(HttpStatus esperado, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    // ── Consultar ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("consultar: lista lo devolvible (sin servicios) con lo que queda por devolver")
    void consultar() {
        service.solicitar(reembolso(linea(10, 1, true)), "yamilet");

        VentaDevolvibleResponseDto r = service.consultarVenta(50, "yamilet");

        assertThat(r.getElegible()).isTrue();
        assertThat(r.getLineas()).extracting(VentaDevolvibleResponseDto.LineaDto::getCodpro).containsExactly("FUN-000001", "CEL-000004");
        assertThat(r.getLineas().get(0).getDisponible()).isEqualTo(2);
        assertThat(r.getDevolucionHasta()).isEqualTo(venta.getFechaVenta().toLocalDate().plusDays(15));
    }

    @Test
    @DisplayName("consultar: una venta cancelada, a crédito, de una orden o fuera de plazo no es elegible")
    void noElegibles() {
        venta.setEstado((byte) 2);
        assertThat(service.consultarVenta(50, "yamilet").getMotivo()).contains("cancelada");
        venta.setEstado((byte) 1);

        when(cuentaPorCobrarRepository.findByVenta_Idventa(50)).thenReturn(Optional.of(new CuentaPorCobrar()));
        assertThat(service.consultarVenta(50, "yamilet").getMotivo()).contains("crédito");
        when(cuentaPorCobrarRepository.findByVenta_Idventa(50)).thenReturn(Optional.empty());

        when(ordenServicioRepository.existsByVenta_Idventa(50)).thenReturn(true);
        assertThat(service.consultarVenta(50, "yamilet").getMotivo()).contains("orden de servicio");
        when(ordenServicioRepository.existsByVenta_Idventa(50)).thenReturn(false);

        venta.setFechaVenta(LocalDateTime.now().minusDays(20));
        assertThat(service.consultarVenta(50, "yamilet").getMotivo()).contains("plazo");
    }

    // ── Solicitar ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("solicitar")
    class Solicitar {

        @Test
        @DisplayName("un reembolso queda Pendiente, con folio y total a los precios de la venta")
        void reembolsoPendiente() {
            DevolucionResponseDto r = service.solicitar(reembolso(linea(10, 2, true)), "yamilet");

            assertThat(r.getEstado()).isEqualTo((byte) 1);
            assertThat(r.getFolio()).isEqualTo("DEV-000001");
            assertThat(r.getTotalDevuelto()).isEqualByComparingTo("200");
            assertThat(r.getMetodoReembolso()).isEqualTo((byte) 1);
            verifyNoInteractions(movimientoCajaService);
        }

        @Test
        @DisplayName("no se puede devolver más de lo vendido ni de lo que ya está en trámite")
        void demasiado() {
            service.solicitar(reembolso(linea(10, 2, true)), "yamilet");

            assertEstado(HttpStatus.BAD_REQUEST, () -> service.solicitar(reembolso(linea(10, 2, true)), "yamilet"));
            service.solicitar(reembolso(linea(10, 1, true)), "yamilet");
        }

        @Test
        @DisplayName("un servicio, un renglón ajeno o repetido, o una unidad con cantidad > 1: 400")
        void renglonesInvalidos() {
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.solicitar(reembolso(linea(12, 1, true)), "yamilet"));
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.solicitar(reembolso(linea(99, 1, true)), "yamilet"));
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.solicitar(reembolso(linea(10, 1, true), linea(10, 1, true)), "yamilet"));
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.solicitar(reembolso(linea(11, 2, true)), "yamilet"));
        }

        @Test
        @DisplayName("una venta fuera de plazo o cancelada: 409")
        void ventaNoElegible() {
            venta.setFechaVenta(LocalDateTime.now().minusDays(16));
            assertEstado(HttpStatus.CONFLICT, () -> service.solicitar(reembolso(linea(10, 1, true)), "yamilet"));
        }

        @Test
        @DisplayName("solo se opera la propia tienda: un encargado de otra tienda, 403")
        void otraTienda() {
            assertEstado(HttpStatus.FORBIDDEN, () -> service.solicitar(reembolso(linea(10, 1, true)), "guillermo"));
        }

        @Test
        @DisplayName("cambio: lo nuevo no puede valer menos que lo devuelto; un equipo exige su IMEI")
        void cambioValidaciones() {
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.solicitar(solicitudCambio("50", linea(10, 1, true)), "yamilet"));

            DevolucionRequestDto dto = solicitudCambio("500", linea(10, 1, true));
            when(productoMasterRepository.findById(2)).thenReturn(Optional.of(mCelular));
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.solicitar(dto, "yamilet"));
        }

        @Test
        @DisplayName("cambio: guarda el producto nuevo y la diferencia")
        void cambioValido() {
            DevolucionResponseDto r = service.solicitar(solicitudCambio("150", linea(10, 1, true)), "yamilet");

            assertThat(r.getTipo()).isEqualTo((byte) 2);
            assertThat(r.getTotalCambio()).isEqualByComparingTo("150");
            assertThat(r.getDiferencia()).isEqualByComparingTo("50");
            assertThat(r.getMetodoDiferencia()).isEqualTo((byte) 1);
        }
    }

    // ── Aprobar / rechazar ────────────────────────────────────────────────────

    @Nested
    @DisplayName("aprobar y rechazar")
    class Resolver {

        @Test
        @DisplayName("un vendedor no resuelve: 403; un encargado de otra tienda, 403")
        void permisos() {
            int id = service.solicitar(reembolso(linea(10, 1, true)), "yamilet").getIddevolucion();

            assertEstado(HttpStatus.FORBIDDEN, () -> service.aprobar(id, null, "yamilet"));
            assertEstado(HttpStatus.FORBIDDEN, () -> service.aprobar(id, null, "guillermo"));
        }

        @Test
        @DisplayName("reembolso en efectivo: el accesorio regresa al inventario y el dinero sale de la caja")
        void reembolsoEfectivo() {
            int id = service.solicitar(reembolso(linea(10, 2, true)), "yamilet").getIddevolucion();

            DevolucionResponseDto r = service.aprobar(id, null, "abigail");

            assertThat(r.getEstado()).isEqualTo((byte) 2);
            assertThat(r.getNombreResuelve()).isEqualTo("Abigail");
            assertThat(pAccesorio.getStock()).isEqualByComparingTo("12");
            verify(movimientoCajaService).registrarDevolucionVenta(eq(caja), any(Devolucion.class), eq(encargado), eq(new BigDecimal("200")));
            verify(movimientoInventarioService).registrar(eq(pAccesorio), eq(new BigDecimal("10")), eq("DEVOLUCION"), anyString(), eq("DEV-000001"));
        }

        @Test
        @DisplayName("reembolso por tarjeta: no mueve la caja")
        void reembolsoTarjeta() {
            DevolucionRequestDto dto = reembolso(linea(10, 1, true));
            dto.setMetodoReembolso((byte) 2);
            int id = service.solicitar(dto, "yamilet").getIddevolucion();

            service.aprobar(id, null, "root");

            verifyNoInteractions(movimientoCajaService);
        }

        @Test
        @DisplayName("un equipo devuelto en buen estado vuelve DISPONIBLE; uno dañado se da de baja sin tocar el stock")
        void equipos() {
            int id = service.solicitar(reembolso(linea(11, 1, true)), "yamilet").getIddevolucion();
            service.aprobar(id, null, "abigail");
            assertThat(imei.getEstado()).isEqualTo("DISPONIBLE");
            assertThat(pCelular.getStock()).isEqualByComparingTo("1");

            imei.setEstado("VENDIDO");
            pCelular.setStock(BigDecimal.ZERO);
            detalles.clear();
            int id2 = service.solicitar(reembolso(linea(11, 1, false)), "yamilet").getIddevolucion();
            service.aprobar(id2, null, "abigail");
            assertThat(imei.getEstado()).isEqualTo("DEBAJA");
            assertThat(pCelular.getStock()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("una unidad que ya no figura como vendida: 409 y nada se aplica")
        void unidadYaNoVendida() {
            int id = service.solicitar(reembolso(linea(10, 1, true), linea(11, 1, true)), "yamilet").getIddevolucion();
            imei.setEstado("DISPONIBLE");

            assertEstado(HttpStatus.CONFLICT, () -> service.aprobar(id, null, "abigail"));
        }

        @Test
        @DisplayName("cambio: se genera la venta nueva con el crédito por devolución y la diferencia")
        void cambio() {
            int id = service.solicitar(solicitudCambio("150", linea(10, 1, true)), "yamilet").getIddevolucion();
            when(ventaService.crearDesdeDevolucion(any(VentaRequestDto.class), anyInt()))
                    .thenReturn(VentaResponseDto.builder().idventa(77).build());

            DevolucionResponseDto r = service.aprobar(id, null, "abigail");

            ArgumentCaptor<VentaRequestDto> cap = ArgumentCaptor.forClass(VentaRequestDto.class);
            verify(ventaService).crearDesdeDevolucion(cap.capture(), eq(4));
            VentaRequestDto v = cap.getValue();
            assertThat(v.getMetodoPago()).isEqualTo((byte) 4);
            assertThat(v.getIdcliente()).isEqualTo(6);
            assertThat(v.getPagos()).hasSize(2);
            assertThat(v.getPagos().get(0).getMetodoPago()).isEqualTo((byte) 7);
            assertThat(v.getPagos().get(0).getMonto()).isEqualByComparingTo("100");
            assertThat(v.getPagos().get(1).getMetodoPago()).isEqualTo((byte) 1);
            assertThat(v.getPagos().get(1).getMonto()).isEqualByComparingTo("50");
            assertThat(r.getIdventaCambio()).isEqualTo(77);
            verify(movimientoCajaService, never()).registrarDevolucionVenta(any(), any(), any(), any());
        }

        @Test
        @DisplayName("cambio por el mismo valor: solo el crédito, sin diferencia")
        void cambioMismoValor() {
            int id = service.solicitar(solicitudCambio("100", linea(10, 1, true)), "yamilet").getIddevolucion();
            when(ventaService.crearDesdeDevolucion(any(VentaRequestDto.class), anyInt()))
                    .thenReturn(VentaResponseDto.builder().idventa(78).build());

            service.aprobar(id, null, "abigail");

            ArgumentCaptor<VentaRequestDto> cap = ArgumentCaptor.forClass(VentaRequestDto.class);
            verify(ventaService).crearDesdeDevolucion(cap.capture(), anyInt());
            assertThat(cap.getValue().getPagos()).hasSize(1);
        }

        @Test
        @DisplayName("si la venta se canceló mientras tanto: 409 al aprobar")
        void ventaCancelada() {
            int id = service.solicitar(reembolso(linea(10, 1, true)), "yamilet").getIddevolucion();
            venta.setEstado((byte) 2);

            assertEstado(HttpStatus.CONFLICT, () -> service.aprobar(id, null, "abigail"));
        }

        @Test
        @DisplayName("rechazar exige el motivo; una devolución rechazada libera lo que ocupaba y no se resuelve dos veces")
        void rechazar() {
            int id = service.solicitar(reembolso(linea(10, 3, true)), "yamilet").getIddevolucion();
            ResolverDevolucionDto sinMotivo = new ResolverDevolucionDto();
            assertEstado(HttpStatus.BAD_REQUEST, () -> service.rechazar(id, sinMotivo, "abigail"));

            ResolverDevolucionDto motivo = new ResolverDevolucionDto();
            motivo.setComentario("Producto usado");
            DevolucionResponseDto r = service.rechazar(id, motivo, "abigail");

            assertThat(r.getEstado()).isEqualTo((byte) 3);
            assertThat(r.getComentarioResolucion()).isEqualTo("Producto usado");
            assertEstado(HttpStatus.CONFLICT, () -> service.aprobar(id, null, "abigail"));
            assertThat(service.consultarVenta(50, "yamilet").getLineas().get(0).getDisponible()).isEqualTo(3);
        }
    }

    // ── Listar ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: el administrador ve todas; los demás, solo su tienda")
    void listar() {
        when(devolucionRepository.buscar(any(), any())).thenReturn(List.of());

        service.listar(null, null, "root");
        verify(devolucionRepository).buscar(null, null);
        service.listar(null, null, "yamilet");
        verify(devolucionRepository).buscar(2, null);
        assertEstado(HttpStatus.FORBIDDEN, () -> service.listar(3, null, "yamilet"));
    }
}
