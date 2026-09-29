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
        a.setEstado(InventarioFisicoService.ESTADO_CATALOGANDO);
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.escanear(10, "ACC-001", null, "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(productoRepository);
    }

    // ── Cerrar conteo / resolver / finalizar ────────────────────────────────

    @Test
    @DisplayName("cerrarConteo: pasa de ABIERTO a CATALOGANDO")
    void cerrarConteo_cambiaEstado() {
        InventarioAuditoria a = auditoriaAbierta();
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10)).thenReturn(List.of());

        Map<String, Object> r = service.cerrarConteo(10, "admin");

        assertThat(r.get("estado")).isEqualTo(InventarioFisicoService.ESTADO_CATALOGANDO);
    }

    private InventarioAuditoria auditoriaCatalogando() {
        InventarioAuditoria a = auditoriaAbierta();
        a.setEstado(InventarioFisicoService.ESTADO_CATALOGANDO);
        return a;
    }

    @Test
    @DisplayName("resolverDetalle: AJUSTAR pone el stock en el conteo y registra el movimiento")
    void resolverDetalle_ajustar_actualizaStock() {
        InventarioAuditoria a = auditoriaCatalogando();
        InventarioAuditoriaDetalle d = InventarioAuditoriaDetalle.builder()
                .iddetalleInv(500).inventarioAuditoria(a).codpro("ACC-001").conteo((short) 8).stockSistema((short) 5)
                .estado(InventarioFisicoService.DETALLE_PENDIENTE).build();
        Producto p = accesorio("ACC-001", new BigDecimal("5"));
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findById(500)).thenReturn(Optional.of(d));
        when(productoRepository.findByCodproAndTienda_Codti("ACC-001", 2)).thenReturn(Optional.of(p));

        Map<String, Object> r = service.resolverDetalle(10, 500, "AJUSTAR", null, "admin");

        assertThat(p.getStock()).isEqualByComparingTo("8");
        assertThat(r.get("estado")).isEqualTo(InventarioFisicoService.DETALLE_AJUSTADO);
        verify(movimientoInventarioService).registrar(eq(p), eq(new BigDecimal("5")), eq("AJUSTE"), anyString(), anyString());
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
    @DisplayName("finalizar: todo catalogado -> cuenta faltantes/sobrantes y cierra")
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
        when(auditoriaRepository.findById(10)).thenReturn(Optional.of(a));
        when(detalleRepository.findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(10))
                .thenReturn(List.of(faltante, sobrante, sinDiferencia));

        Map<String, Object> r = service.finalizar(10, "admin");

        assertThat(r.get("estado")).isEqualTo(InventarioFisicoService.ESTADO_FINALIZADO);
        assertThat(r.get("totalFaltantes")).isEqualTo(1);
        assertThat(r.get("totalSobrantes")).isEqualTo(1);
    }

    /** Mockito matcher chico: exactamente ese codti. */
    private static Integer eq2(Integer codti) {
        return org.mockito.ArgumentMatchers.eq(codti);
    }
}
