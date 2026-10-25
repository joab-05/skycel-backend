package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.SolicitudDescuento;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.descuento.SolicitudDescuentoRequestDto;
import com.skycel.backend.repository.ProductoMasterRepository;
import com.skycel.backend.repository.SolicitudDescuentoRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolicitudDescuentoServiceTest {

    @Mock private SolicitudDescuentoRepository repository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TiendaRepository tiendaRepository;
    @Mock private ProductoMasterRepository productoMasterRepository;
    @Mock private NotificacionService notificacionService;
    @InjectMocks private SolicitudDescuentoService service;

    private Tienda tienda;
    private Usuario vendedor, admin, otro;
    private ProductoMaster master;

    @BeforeEach
    void setUp() {
        tienda = Tienda.builder().codti(2).nombre("Zócalo").build();
        vendedor = Usuario.builder().idusuario(10).username("vend").nombreCompleto("Ana").rol(Rol.VENDEDOR).tienda(tienda).build();
        otro = Usuario.builder().idusuario(11).username("otro").nombreCompleto("Luis").rol(Rol.VENDEDOR).tienda(tienda).build();
        admin = Usuario.builder().idusuario(1).username("admin").nombreCompleto("Admin").rol(Rol.ADMIN).build();
        master = ProductoMaster.builder().idprodmaster(5).tipo(TipoProducto.ACCESORIO).nombreBase("Cable USB-C").build();
        lenient().when(usuarioRepository.findByUsername("vend")).thenReturn(Optional.of(vendedor));
        lenient().when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        lenient().when(usuarioRepository.findByUsername("otro")).thenReturn(Optional.of(otro));
        lenient().when(tiendaRepository.findById(2)).thenReturn(Optional.of(tienda));
        lenient().when(productoMasterRepository.findById(5)).thenReturn(Optional.of(master));
        lenient().when(repository.save(any(SolicitudDescuento.class))).thenAnswer(i -> {
            SolicitudDescuento s = i.getArgument(0);
            if (s.getIdsolicitud() == null) s.setIdsolicitud(40);
            return s;
        });
    }

    private SolicitudDescuentoRequestDto pedido(String descuento) {
        SolicitudDescuentoRequestDto d = new SolicitudDescuentoRequestDto();
        d.setCodti(2);
        d.setIdprodmaster(5);
        d.setCantidad((short) 1);
        d.setPrecioLista(new BigDecimal("100"));
        d.setDescuentoPorPieza(new BigDecimal(descuento));
        d.setMotivo("Cliente frecuente");
        return d;
    }

    private SolicitudDescuento guardada(byte estado) {
        return SolicitudDescuento.builder().idsolicitud(40).tienda(tienda).solicitante(vendedor).productoMaster(master)
                .cantidad((short) 1).precioLista(new BigDecimal("100")).descuentoPorPieza(new BigDecimal("20"))
                .motivo("x").estado(estado).fechaResolucion(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("pedir: queda pendiente y se avisa a los administradores")
    void crear() {
        Map<String, Object> r = service.crear(pedido("20"), "vend");

        assertThat(r.get("estadoDisplay")).isEqualTo("Pendiente");
        verify(notificacionService).notificarRol(eq(Rol.ADMIN), eq("DESCUENTO_SOLICITUD"), any(), eq("#/descuentos"));
        verify(notificacionService).notificarRol(eq(Rol.ROOT), eq("DESCUENTO_SOLICITUD"), any(), eq("#/descuentos"));
    }

    @Test
    @DisplayName("pedir: sin motivo, o un descuento igual o mayor al precio, 400")
    void crearInvalido() {
        SolicitudDescuentoRequestDto sinMotivo = pedido("20");
        sinMotivo.setMotivo(" ");
        assertThatThrownBy(() -> service.crear(sinMotivo, "vend")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("motivo");
        assertThatThrownBy(() -> service.crear(pedido("100"), "vend")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("menor al precio");
    }

    @Test
    @DisplayName("aprobar: con otro monto lo cambia y avisa a quien lo pidió")
    void aprobarConOtroMonto() {
        when(repository.findById(40)).thenReturn(Optional.of(guardada(SolicitudDescuento.PENDIENTE)));

        Map<String, Object> r = service.aprobar(40, new BigDecimal("15"), "admin");

        assertThat(r.get("estadoDisplay")).isEqualTo("Aprobada");
        assertThat((BigDecimal) r.get("descuentoPorPieza")).isEqualByComparingTo("15");
        verify(notificacionService).notificarUsuario(eq(vendedor), eq("DESCUENTO_APROBADO"), any(), any());
    }

    @Test
    @DisplayName("aprobar o rechazar una solicitud ya resuelta: 409")
    void yaResuelta() {
        when(repository.findById(40)).thenReturn(Optional.of(guardada(SolicitudDescuento.RECHAZADA)));

        assertThatThrownBy(() -> service.aprobar(40, null, "admin")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya se resolvió");
        assertThatThrownBy(() -> service.rechazar(40, "no", "admin")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya se resolvió");
    }

    @Test
    @DisplayName("rechazar: guarda el motivo y avisa a quien lo pidió")
    void rechazar() {
        when(repository.findById(40)).thenReturn(Optional.of(guardada(SolicitudDescuento.PENDIENTE)));

        Map<String, Object> r = service.rechazar(40, "Sin margen", "admin");

        assertThat(r.get("estadoDisplay")).isEqualTo("Rechazada");
        assertThat(r.get("motivoResolucion")).isEqualTo("Sin margen");
        verify(notificacionService).notificarUsuario(eq(vendedor), eq("DESCUENTO_RECHAZADO"), any(), any());
    }

    @Test
    @DisplayName("validar para la venta: una aprobación vigente del mismo vendedor, sucursal y artículo sirve")
    void validarOk() {
        when(repository.findById(40)).thenReturn(Optional.of(guardada(SolicitudDescuento.APROBADA)));

        assertThat(service.validarParaVenta(40, vendedor, 2, master, null).getIdsolicitud()).isEqualTo(40);
    }

    @Test
    @DisplayName("validar para la venta: de otro vendedor, otra sucursal, ya usada o vencida, 400")
    void validarRechaza() {
        when(repository.findById(40)).thenReturn(Optional.of(guardada(SolicitudDescuento.APROBADA)));
        assertThatThrownBy(() -> service.validarParaVenta(40, otro, 2, master, null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("no corresponde");
        assertThatThrownBy(() -> service.validarParaVenta(40, vendedor, 3, master, null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("no corresponde");

        when(repository.findById(41)).thenReturn(Optional.of(guardada(SolicitudDescuento.USADA)));
        assertThatThrownBy(() -> service.validarParaVenta(41, vendedor, 2, master, null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("ya se usó");

        SolicitudDescuento vencida = guardada(SolicitudDescuento.APROBADA);
        vencida.setFechaResolucion(LocalDateTime.now().minusHours(13));
        when(repository.findById(42)).thenReturn(Optional.of(vencida));
        assertThatThrownBy(() -> service.validarParaVenta(42, vendedor, 2, master, null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("venció");
    }

    @Test
    @DisplayName("un vendedor no ve las solicitudes de otro; un administrador sí")
    void verSolicitud() {
        when(repository.findById(40)).thenReturn(Optional.of(guardada(SolicitudDescuento.PENDIENTE)));

        assertThat(service.obtener(40, "admin").get("idsolicitud")).isEqualTo(40);
        assertThatThrownBy(() -> service.obtener(40, "otro")).isInstanceOf(ResponseStatusException.class);
    }
}
