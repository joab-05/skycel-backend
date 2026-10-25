package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas unitarias del inventario físico por secciones (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class InventarioFisicoServiceTest {

    @Mock private InventarioAuditoriaRepository auditoriaRepository;
    @Mock private InventarioAuditoriaDetalleRepository detalleRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private TiendaRepository tiendaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MovimientoInventarioService movimientoInventarioService;
    @Mock private InventarioAuditoriaUnidadRepository unidadRepository;
    @Mock private ProductoImeiRepository imeiRepository;
    @Mock private NotificacionService notificacionService;
    @Mock private PermisoService permisoService;
    @Mock private CategoriaRepository categoriaRepository;

    @InjectMocks
    private InventarioFisicoService service;

    private Tienda zocalo;
    private Usuario admin, encargadoZocalo, encargadoOtra;

    @BeforeEach
    void setUp() {
        zocalo = Tienda.builder().codti(2).nombre("Zócalo").build();
        admin = Usuario.builder().idusuario(1).username("admin").nombreCompleto("Admin").rol(Rol.ADMIN).build();
        encargadoZocalo = Usuario.builder().idusuario(2).username("enc2").nombreCompleto("Encargado Zócalo").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        Tienda otra = Tienda.builder().codti(9).nombre("Otra").build();
        encargadoOtra = Usuario.builder().idusuario(3).username("enc9").nombreCompleto("Encargado Otra").rol(Rol.ENCARGADO_TIENDA).tienda(otra).build();

        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("enc2")).thenReturn(Optional.of(encargadoZocalo));
        lenient().when(usuarioRepository.findByUsername("enc9")).thenReturn(Optional.of(encargadoOtra));
        lenient().when(tiendaRepository.findById(2)).thenReturn(Optional.of(zocalo));
        // solo el administrador confirma; el encargado cuenta
        lenient().when(permisoService.tiene(any(Rol.class), anyString())).thenAnswer(inv ->
                inv.getArgument(0) == Rol.ADMIN || inv.getArgument(0) == Rol.ROOT);
        lenient().when(auditoriaRepository.save(any(InventarioAuditoria.class))).thenAnswer(inv -> {
            InventarioAuditoria a = inv.getArgument(0);
            if (a.getIdinventario() == null) a.setIdinventario(100);
            return a;
        });
        lenient().when(detalleRepository.save(any(InventarioAuditoriaDetalle.class))).thenAnswer(inv -> {
            InventarioAuditoriaDetalle d = inv.getArgument(0);
            if (d.getIddetalleInv() == null) d.setIddetalleInv(500);
            return d;
        });
    }

    private InventarioAuditoria auditoriaAbierta() {
        return InventarioAuditoria.builder()
                .idinventario(10).tienda(zocalo).usuarioAuditor(admin).usuarioEncargadoTienda(encargadoZocalo)
                .estado(InventarioFisicoService.ESTADO_ABIERTO).totalFaltantes(0).totalSobrantes(0).build();
    }

    private Producto accesorio(String codpro, BigDecimal stock) {
        ProductoMaster master = ProductoMaster.builder().idprodmaster(1).tipo(TipoProducto.ACCESORIO).nombreBase("Cable USB-C").build();
        Seccion seccion = Seccion.builder().idseccion((short) 1).nombre("Accesorios").tienda(zocalo).build();
        return Producto.builder().idproducto(50).codpro(codpro).tienda(zocalo).productoMaster(master).stock(stock).seccion(seccion).build();
    }

    // ── Aperturar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aperturar: crea la auditoría en estado ABIERTO")
    void aperturar_creaAbierta() {
        when(auditoriaRepository.findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(eq2(2), any())).thenReturn(Optional.empty());

        Map<String, Object> r = service.abrir(2, null, "admin");

        assertThat(r.get("estado")).isEqualTo(InventarioFisicoService.ESTADO_ABIERTO);
        assertThat(r.get("codti")).isEqualTo(2);
    }

    @Test
    @DisplayName("aperturar: si ya hay una en curso para la sucursal -> 409")
    void aperturar_yaHayEnCurso_lanza409() {
        when(auditoriaRepository.findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(eq2(2), any()))
                .thenReturn(Optional.of(auditoriaAbierta()));

        assertThatThrownBy(() -> service.abrir(2, null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("aperturar: un encargado de otra sucursal -> 403")
    void aperturar_encargadoOtraSucursal_lanza403() {
        assertThatThrownBy(() -> service.abrir(2, null, "enc9"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── Alcance por categorías ───────────────────────────────────────────────

    private Categoria micas() {
        Categoria accesorios = Categoria.builder().idcat((short) 1).nombre("Accesorios").build();
        return Categoria.builder().idcat((short) 7).nombre("Micas").categoriaSuperior(accesorios).build();
    }

    private InventarioAuditoria auditoriaSoloMicas() {
        InventarioAuditoria a = auditoriaAbierta();
        a.setAlcance("7");
        lenient().when(categoriaRepository.findById((short) 7)).thenReturn(Optional.of(micas()));
        return a;
    }

    private Producto accesorioDe(String codpro, Categoria categoria, BigDecimal stock, Seccion seccion) {
        ProductoMaster master = ProductoMaster.builder().idprodmaster(9).tipo(TipoProducto.ACCESORIO).nombreBase("Mica " + codpro)
                .categoria(categoria).build();
        return Producto.builder().idproducto(60).codpro(codpro).tienda(zocalo).productoMaster(master).stock(stock).seccion(seccion).build();
    }

    @Test
    @DisplayName("aperturar con categorías: guarda el alcance (solo micas, solo equipos...)")
    void aperturar_conAlcance() {
        when(auditoriaRepository.findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(eq2(2), any())).thenReturn(Optional.empty());
        when(categoriaRepository.findById((short) 7)).thenReturn(Optional.of(micas()));

        Map<String, Object> r = service.abrir(2, null, List.of((short) 7), "admin");

        assertThat(r.get("alcance")).isEqualTo("Micas");
    }

    @Test
    @DisplayName("aperturar con una categoría que no existe: 400")
    void aperturar_categoriaInexistente() {
        when(auditoriaRepository.findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(eq2(2), any())).thenReturn(Optional.empty());
        when(categoriaRepository.findById((short) 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abrir(2, null, List.of((short) 99), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no existe");
    }

    @Test
    @DisplayName("aperturar con una categoría de servicios: 400 (no tienen conteo físico)")
    void aperturar_conServicios() {
        when(auditoriaRepository.findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(eq2(2), any())).thenReturn(Optional.empty());
        Categoria servicios = Categoria.builder().idcat((short) 20).nombre("Servicios").tipo(TipoProducto.SERVICIO).build();
        Categoria cambio = Categoria.builder().idcat((short) 21).nombre("Cambio de pantalla").categoriaSuperior(servicios).build();
        when(categoriaRepository.findById((short) 21)).thenReturn(Optional.of(cambio));

        assertThatThrownBy(() -> service.abrir(2, null, List.of((short) 21), "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("servicios");
    }

    @Test
    @DisplayName("escanear con alcance: un artículo de otra categoría se rechaza (400)")
    void escanear_fueraDelAlcance() {
        InventarioAuditoria soloMicas = auditoriaSoloMicas();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(soloMicas));
        Categoria cargadores = Categoria.builder().idcat((short) 8).nombre("Cargadores").build();
        when(productoRepository.findByCodproAndTienda_Codti("CAR-1", 2)).thenReturn(Optional.of(accesorioDe("CAR-1", cargadores, BigDecimal.TEN, null)));

        assertThatThrownBy(() -> service.escanear(10, "CAR-1", null, "admin"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no pertenece").hasMessageContaining("Micas");
    }

    @Test
    @DisplayName("escanear con alcance: un artículo de la categoría se cuenta aunque no tenga sección")
    void escanear_dentroDelAlcance_sinSeccion() {
        InventarioAuditoria soloMicas = auditoriaSoloMicas();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(soloMicas));
        when(productoRepository.findByCodproAndTienda_Codti("MIC-1", 2)).thenReturn(Optional.of(accesorioDe("MIC-1", micas(), new BigDecimal("4"), null)));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(10, "MIC-1")).thenReturn(Optional.empty());

        Map<String, Object> r = service.escanear(10, "MIC-1", null, "admin");

        assertThat(r.get("conteo")).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("cerrarConteo con alcance: lo que hay en esas categorías y no se contó queda en 0; lo de otras categorías no se toca")
    void cerrarConteo_conAlcance() {
        InventarioAuditoria a = auditoriaSoloMicas();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10)).thenReturn(new java.util.ArrayList<>());
        Categoria cargadores = Categoria.builder().idcat((short) 8).nombre("Cargadores").build();
        when(productoRepository.findByTienda_CodtiAndActivoTrue(2)).thenReturn(List.of(
                accesorioDe("MIC-1", micas(), new BigDecimal("4"), null),
                accesorioDe("CAR-1", cargadores, new BigDecimal("6"), null)));
        when(imeiRepository.deLaSucursal(eq(2), any())).thenReturn(List.of());

        Map<String, Object> r = service.cerrarConteo(10, "admin");

        assertThat(r.get("totalFaltantes")).isEqualTo(1);
        org.mockito.ArgumentCaptor<InventarioAuditoriaDetalle> cap = org.mockito.ArgumentCaptor.forClass(InventarioAuditoriaDetalle.class);
        verify(detalleRepository, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(InventarioAuditoriaDetalle::getCodpro).containsExactly("MIC-1");
    }

    // ── Escanear ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("escanear: primera vez crea el detalle con el stock del sistema como snapshot")
    void escanear_primeraVez_creaDetalle() {
        InventarioAuditoria a = auditoriaAbierta();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(productoRepository.findByCodproAndTienda_Codti("ACC-001", 2)).thenReturn(Optional.of(accesorio("ACC-001", new BigDecimal("5"))));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(10, "ACC-001")).thenReturn(Optional.empty());

        Map<String, Object> r = service.escanear(10, "ACC-001", null, "admin");

        assertThat(r.get("conteo")).isEqualTo((short) 1);
        assertThat(r.get("stockSistema")).isEqualTo((short) 5);
        assertThat(r.get("diferencia")).isEqualTo(-4);
    }

    @Test
    @DisplayName("escanear un IMEI: cuenta el equipo en su artículo (stock del sistema = disponibles de la sucursal) y no se cuenta dos veces")
    void escanear_equipo() {
        InventarioAuditoria a = auditoriaAbierta();
        ProductoMaster celular = ProductoMaster.builder().idprodmaster(7).tipo(TipoProducto.CELULAR).nombreBase("Celular A15").build();
        ProductoImei u = ProductoImei.builder().imei("350000000000011").productoMaster(celular).tienda(zocalo).estado("DISPONIBLE").build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(imeiRepository.findByImei("350000000000011")).thenReturn(Optional.of(u));
        when(imeiRepository.countByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(7, 2, "DISPONIBLE")).thenReturn(3L);

        Map<String, Object> r = service.escanear(10, "350000000000011", null, "enc2");

        assertThat(r.get("conteo")).isEqualTo((short) 1);
        assertThat(r.get("stockSistema")).isEqualTo((short) 3);
        assertThat(r.get("enSistema")).isEqualTo(true);
        verify(unidadRepository).save(any(InventarioAuditoriaUnidad.class));

        when(unidadRepository.findByInventarioAuditoria_IdinventarioAndImei(10, "350000000000011"))
                .thenReturn(Optional.of(InventarioAuditoriaUnidad.builder().imei("350000000000011").build()));
        assertThatThrownBy(() -> service.escanear(10, "350000000000011", null, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("escanear: un código que no es producto ni IMEI -> 404")
    void escanear_noExiste() {
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(auditoriaAbierta()));

        assertThatThrownBy(() -> service.escanear(10, "NOEXISTE", null, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("escanear: si ya se había escaneado, acumula el conteo")
    void escanear_yaExiste_acumula() {
        InventarioAuditoria a = auditoriaAbierta();
        InventarioAuditoriaDetalle existente = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(500).inventarioAuditoria(a).codpro("ACC-001").conteo((short) 3).stockSistema((short) 5)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(productoRepository.findByCodproAndTienda_Codti("ACC-001", 2)).thenReturn(Optional.of(accesorio("ACC-001", new BigDecimal("5"))));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioAndCodpro(10, "ACC-001")).thenReturn(Optional.of(existente));

        Map<String, Object> r = service.escanear(10, "ACC-001", 2, "admin");

        assertThat(r.get("conteo")).isEqualTo((short) 5);
        assertThat(r.get("diferencia")).isEqualTo(0);
    }

    @Test
    @DisplayName("escanear: un artículo sin sección asignada -> 400 con mensaje claro (en vez de tronar por la columna NOT NULL)")
    void escanear_sinSeccion_lanza400() {
        InventarioAuditoria a = auditoriaAbierta();
        Producto sinSeccion = accesorio("ACC-002", new BigDecimal("3"));
        sinSeccion.setSeccion(null);
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(productoRepository.findByCodproAndTienda_Codti("ACC-002", 2)).thenReturn(Optional.of(sinSeccion));

        assertThatThrownBy(() -> service.escanear(10, "ACC-002", null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(detalleRepository);
    }

    @Test
    @DisplayName("escanear: si la auditoría ya no está ABIERTA -> 400")
    void escanear_noAbierta_lanza400() {
        InventarioAuditoria a = auditoriaAbierta();
        a.setEstado(InventarioFisicoService.ESTADO_EN_REVISION);
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.escanear(10, "ACC-001", null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(productoRepository);
    }

    // ── Cerrar conteo / resolver / finalizar ────────────────────────────────

    @Test
    @DisplayName("cerrarConteo: pasa de ABIERTO a EN REVISIÓN, avisa a los administradores y no toca las existencias")
    void cerrarConteo_cambiaEstado() {
        InventarioAuditoria a = auditoriaAbierta();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10)).thenReturn(List.of());

        Map<String, Object> r = service.cerrarConteo(10, "admin");

        assertThat(r.get("estado")).isEqualTo(InventarioFisicoService.ESTADO_EN_REVISION);
        verify(notificacionService).notificarRol(eq(Rol.ADMIN), eq("INVENTARIO_REVISION"), anyString(), anyString());
        verifyNoInteractions(movimientoInventarioService);
    }

    @Test
    @DisplayName("cerrarConteo: lo que no se escaneó en las secciones contadas queda en 0 (faltante)")
    void cerrarConteo_agregaLoQueNoSeEncontro() {
        InventarioAuditoria a = auditoriaAbierta();
        Producto contado = accesorio("ACC-001", new BigDecimal("5"));
        Producto noEncontrado = accesorio("ACC-009", new BigDecimal("3"));
        InventarioAuditoriaDetalle d = InventarioAuditoriaDetalle.builder().iddetalleInv(500).inventarioAuditoria(a)
                .seccion(contado.getSeccion()).codpro("ACC-001").conteo((short) 5).stockSistema((short) 5)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10)).thenReturn(List.of(d));
        when(productoRepository.findByTienda_CodtiAndSeccion_IdseccionAndActivoTrue(2, (short) 1)).thenReturn(List.of(contado, noEncontrado));

        Map<String, Object> r = service.cerrarConteo(10, "enc2");

        assertThat(r.get("totalFaltantes")).isEqualTo(1);
        assertThat(r.get("totalArticulos")).isEqualTo(2);
    }

    private InventarioAuditoria auditoriaCatalogando() {
        InventarioAuditoria a = auditoriaAbierta();
        a.setEstado(InventarioFisicoService.ESTADO_CATALOGANDO);
        return a;
    }

    @Test
    @DisplayName("resolverDetalle: AJUSTAR solo deja la decisión; las existencias no cambian hasta cerrar el inventario")
    void resolverDetalle_ajustar_soloDecide() {
        InventarioAuditoria a = auditoriaCatalogando();
        InventarioAuditoriaDetalle d = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(500).inventarioAuditoria(a).codpro("ACC-001").conteo((short) 8).stockSistema((short) 5)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findById(500)).thenReturn(Optional.of(d));

        Map<String, Object> r = service.resolverDetalle(10, 500, "AJUSTAR", null, "admin");

        assertThat(r.get("estado")).isEqualTo(InventarioFisicoService.DETALLE_AJUSTADO);
        verifyNoInteractions(movimientoInventarioService);
        verifyNoInteractions(productoRepository);
    }

    @Test
    @DisplayName("resolverDetalle: un encargado no decide los ajustes -> 403")
    void resolverDetalle_encargado_lanza403() {
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(auditoriaCatalogando()));
        assertThatThrownBy(() -> service.resolverDetalle(10, 500, "AJUSTAR", null, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("resolverDetalle: un equipo que sobra no se puede ajustar (no se sabe su costo ni precio): solo justificar")
    void resolverDetalle_equipoSobrante() {
        InventarioAuditoria a = auditoriaCatalogando();
        ProductoMaster celular = ProductoMaster.builder().idprodmaster(7).tipo(TipoProducto.CELULAR).nombreBase("Celular A15").build();
        InventarioAuditoriaDetalle d = InventarioAuditoriaDetalle.builder().iddetalleInv(500).inventarioAuditoria(a)
                .productoMaster(celular).codpro("EQ7").conteo((short) 4).stockSistema((short) 3)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findById(500)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.resolverDetalle(10, 500, "AJUSTAR", null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(service.resolverDetalle(10, 500, "JUSTIFICAR", "Llegó sin registrar", "admin").get("estado"))
                .isEqualTo(InventarioFisicoService.DETALLE_JUSTIFICADO);
    }

    @Test
    @DisplayName("devolver: el administrador regresa el conteo al encargado y las decisiones se borran")
    void devolver_reabre() {
        InventarioAuditoria a = auditoriaCatalogando();
        InventarioAuditoriaDetalle d = InventarioAuditoriaDetalle.builder().iddetalleInv(500).inventarioAuditoria(a)
                .codpro("ACC-001").conteo((short) 8).stockSistema((short) 5).estado(InventarioFisicoService.DETALLE_AJUSTADO).build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10)).thenReturn(List.of(d));

        Map<String, Object> r = service.devolver(10, "Falta la vitrina", "admin");

        assertThat(r.get("estado")).isEqualTo(InventarioFisicoService.ESTADO_ABIERTO);
        assertThat(d.getEstado()).isEqualTo(InventarioFisicoService.DETALLE_PENDIENTE);
        verify(notificacionService).notificarTienda(eq(2), eq("INVENTARIO_DEVUELTO"), contains("Falta la vitrina"), anyString());
    }

    @Test
    @DisplayName("resolverDetalle: JUSTIFICAR sin motivo -> 400")
    void resolverDetalle_justificarSinMotivo_lanza400() {
        InventarioAuditoria a = auditoriaCatalogando();
        InventarioAuditoriaDetalle d = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(500).inventarioAuditoria(a).codpro("ACC-001").conteo((short) 8).stockSistema((short) 5)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findById(500)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.resolverDetalle(10, 500, "JUSTIFICAR", "  ", "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(movimientoInventarioService);
    }

    @Test
    @DisplayName("resolverDetalle: sin diferencia no hay nada que catalogar -> 400")
    void resolverDetalle_sinDiferencia_lanza400() {
        InventarioAuditoria a = auditoriaCatalogando();
        InventarioAuditoriaDetalle d = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(500).inventarioAuditoria(a).codpro("ACC-001").conteo((short) 5).stockSistema((short) 5)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findById(500)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.resolverDetalle(10, 500, "AJUSTAR", null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("finalizar: con diferencias pendientes por catalogar -> 400")
    void finalizar_conPendientes_lanza400() {
        InventarioAuditoria a = auditoriaCatalogando();
        InventarioAuditoriaDetalle pendiente = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(500).inventarioAuditoria(a).codpro("ACC-001").conteo((short) 8).stockSistema((short) 5)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10)).thenReturn(List.of(pendiente));

        assertThatThrownBy(() -> service.finalizar(10, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("finalizar: aplica a las existencias lo decidido como ajustar (la diferencia medida), justifica el resto y cierra")
    void finalizar_todoCatalogado_cierra() {
        InventarioAuditoria a = auditoriaCatalogando();
        InventarioAuditoriaDetalle faltante = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(500).inventarioAuditoria(a).codpro("ACC-001").conteo((short) 3).stockSistema((short) 5)
                .estado(InventarioFisicoService.DETALLE_AJUSTADO).build();
        InventarioAuditoriaDetalle sobrante = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(501).inventarioAuditoria(a).codpro("ACC-002").conteo((short) 9).stockSistema((short) 6)
                .estado(InventarioFisicoService.DETALLE_JUSTIFICADO).build();
        InventarioAuditoriaDetalle sinDiferencia = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(502).inventarioAuditoria(a).codpro("ACC-003").conteo((short) 4).stockSistema((short) 4)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        Producto p = accesorio("ACC-001", new BigDecimal("4"));   // mientras tanto se vendió 1 pieza: el sistema ya dice 4
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10))
                .thenReturn(List.of(faltante, sobrante, sinDiferencia));
        when(productoRepository.findByCodproAndTienda_Codti("ACC-001", 2)).thenReturn(Optional.of(p));

        Map<String, Object> r = service.finalizar(10, "admin");

        assertThat(r.get("estado")).isEqualTo(InventarioFisicoService.ESTADO_FINALIZADO);
        assertThat(r.get("totalFaltantes")).isEqualTo(1);
        assertThat(r.get("totalSobrantes")).isEqualTo(1);
        assertThat(p.getStock()).isEqualByComparingTo("2");   // 4 − 2 (faltaban 2): se respeta la venta de mientras
        verify(movimientoInventarioService).registrar(eq(p), eq(new BigDecimal("4")), eq("AJUSTE"), anyString(), anyString());
        assertThat(a.getUsuarioConfirma()).isSameAs(admin);
    }

    @Test
    @DisplayName("finalizar: los equipos disponibles que no se escanearon se dan de baja")
    void finalizar_equiposFaltantesSeDanDeBaja() {
        InventarioAuditoria a = auditoriaCatalogando();
        ProductoMaster celular = ProductoMaster.builder().idprodmaster(7).tipo(TipoProducto.CELULAR).nombreBase("Celular A15").build();
        InventarioAuditoriaDetalle d = InventarioAuditoriaDetalle.builder().iddetalleInv(500).inventarioAuditoria(a)
                .productoMaster(celular).codpro("EQ7").conteo((short) 1).stockSistema((short) 2)
                .estado(InventarioFisicoService.DETALLE_AJUSTADO).build();
        ProductoImei encontrado = ProductoImei.builder().imei("350000000000011").productoMaster(celular).tienda(zocalo).estado("DISPONIBLE").build();
        ProductoImei perdido = ProductoImei.builder().imei("350000000000029").productoMaster(celular).tienda(zocalo).estado("DISPONIBLE").build();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10)).thenReturn(List.of(d));
        when(unidadRepository.findByInventarioAuditoria_IdinventarioOrderByIdAsc(10)).thenReturn(List.of(
                InventarioAuditoriaUnidad.builder().imei("350000000000011").productoMaster(celular).enSistema(true).build()));
        when(imeiRepository.findByProductoMaster_IdprodmasterAndTienda_CodtiAndEstado(7, 2, "DISPONIBLE")).thenReturn(List.of(encontrado, perdido));

        service.finalizar(10, "admin");

        assertThat(encontrado.getEstado()).isEqualTo("DISPONIBLE");
        assertThat(perdido.getEstado()).isEqualTo("DEBAJA");
        verify(movimientoInventarioService).registrarEquipo(eq(perdido), eq(-1), eq("AJUSTE"), anyString(), anyString());
    }

    @Test
    @DisplayName("finalizar: un encargado no cierra el inventario -> 403")
    void finalizar_encargado_lanza403() {
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(auditoriaCatalogando()));
        assertThatThrownBy(() -> service.finalizar(10, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("cancelar: el encargado cancela mientras cuenta; en revisión solo quien lo confirma")
    void cancelar() {
        InventarioAuditoria abierta = auditoriaAbierta();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(abierta));
        assertThat(service.cancelar(10, "enc2").get("estado")).isEqualTo(InventarioFisicoService.ESTADO_CANCELADO);

        InventarioAuditoria enRevision = auditoriaCatalogando();
        when(auditoriaRepository.findById(11)).thenReturn(Optional.of(enRevision));
        assertThatThrownBy(() -> service.cancelar(11, "enc2"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service.cancelar(11, "admin").get("estado")).isEqualTo(InventarioFisicoService.ESTADO_CANCELADO);
    }

    /** Mockito matcher chico: exactamente ese codti. */
    private static Integer eq2(Integer codti) {
        return org.mockito.ArgumentMatchers.eq(codti);
    }
}
