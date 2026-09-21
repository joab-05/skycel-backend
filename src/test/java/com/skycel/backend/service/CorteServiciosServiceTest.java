package com.skycel.backend.service;

import com.skycel.backend.domain.entity.*;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.servicios.*;
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
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;

/** Corte diario de servicios (sin Spring ni base de datos): repositorios en memoria. */
@ExtendWith(MockitoExtension.class)
class CorteServiciosServiceTest {

    @Mock private ServicioTipoRepository        tipoRepository;
    @Mock private CorteServiciosRepository      corteRepository;
    @Mock private CorteServiciosLineaRepository lineaRepository;
    @Mock private TiendaRepository              tiendaRepository;
    @Mock private UsuarioRepository             usuarioRepository;

    @InjectMocks
    private CorteServiciosService service;

    private final Map<Integer, CorteServicios> cortes = new HashMap<>();
    private final List<CorteServiciosLinea> lineas = new ArrayList<>();
    private int siguiente = 1;

    private Tienda zocalo, corpo;
    private ServicioTipo recargas, pagos, pines;

    @BeforeEach
    void setUp() {
        zocalo = Tienda.builder().codti(2).nombre("Zocalo").build();
        corpo = Tienda.builder().codti(3).nombre("Corpo").build();
        Usuario abigail = Usuario.builder().idusuario(4).username("abigail").nombreCompleto("Abigail").rol(Rol.ENCARGADO_TIENDA).tienda(zocalo).build();
        Usuario root = Usuario.builder().idusuario(1).username("root").nombreCompleto("Root").rol(Rol.ROOT).tienda(zocalo).build();
        Usuario yamilet = Usuario.builder().idusuario(9).username("yamilet").nombreCompleto("Yamilet").rol(Rol.VENDEDOR).tienda(zocalo).build();
        for (Usuario u : List.of(abigail, root, yamilet)) lenient().when(usuarioRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));
        lenient().when(tiendaRepository.findById(2)).thenReturn(Optional.of(zocalo));
        lenient().when(tiendaRepository.findById(3)).thenReturn(Optional.of(corpo));

        recargas = ServicioTipo.builder().idtipo(1).nombre("Recargas").comisionPorOperacion(BigDecimal.ZERO).orden(1).activo(true).build();
        pagos = ServicioTipo.builder().idtipo(2).nombre("Pagos de servicios").comisionPorOperacion(new BigDecimal("15")).orden(2).activo(true).build();
        pines = ServicioTipo.builder().idtipo(3).nombre("Pines electrónicos").comisionPorOperacion(new BigDecimal("15")).orden(3).activo(false).build();
        for (ServicioTipo t : List.of(recargas, pagos, pines)) lenient().when(tipoRepository.findById(t.getIdtipo())).thenReturn(Optional.of(t));

        lenient().when(corteRepository.save(any(CorteServicios.class))).thenAnswer(i -> {
            CorteServicios c = i.getArgument(0);
            if (c.getIdcorte() == null) c.setIdcorte(siguiente++);
            cortes.put(c.getIdcorte(), c);
            return c;
        });
        lenient().when(corteRepository.findById(anyInt())).thenAnswer(i -> Optional.ofNullable(cortes.get((Integer) i.getArgument(0))));
        lenient().when(corteRepository.findByTienda_CodtiAndFecha(anyInt(), any(LocalDate.class))).thenAnswer(i -> cortes.values().stream()
                .filter(c -> c.getTienda().getCodti().equals(i.getArgument(0)) && c.getFecha().equals(i.getArgument(1))).findFirst());
        lenient().when(lineaRepository.save(any(CorteServiciosLinea.class))).thenAnswer(i -> {
            CorteServiciosLinea l = i.getArgument(0);
            if (l.getIdlinea() == null) l.setIdlinea(siguiente++);
            if (!lineas.contains(l)) lineas.add(l);
            return l;
        });
        lenient().when(lineaRepository.findByCorte_IdcorteOrderByIdlinea(anyInt())).thenAnswer(i ->
                lineas.stream().filter(l -> l.getCorte().getIdcorte().equals(i.getArgument(0))).collect(Collectors.toList()));
        lenient().doAnswer(i -> { lineas.removeIf(l -> l.getCorte().getIdcorte().equals(i.getArgument(0))); return null; })
                .when(lineaRepository).deleteByCorte_Idcorte(anyInt());
    }

    private CorteServiciosRequestDto.LineaDto linea(int idtipo, Integer ops, String monto) {
        CorteServiciosRequestDto.LineaDto l = new CorteServiciosRequestDto.LineaDto();
        l.setIdtipo(idtipo);
        l.setOperaciones(ops);
        l.setMonto(new BigDecimal(monto));
        return l;
    }

    private CorteServiciosRequestDto corte(CorteServiciosRequestDto.LineaDto... ls) {
        CorteServiciosRequestDto dto = new CorteServiciosRequestDto();
        dto.setLineas(List.of(ls));
        return dto;
    }

    private ConfirmarCorteRequestDto confirmacion(CorteServiciosResponseDto c, String comentario, String... montos) {
        ConfirmarCorteRequestDto dto = new ConfirmarCorteRequestDto();
        List<ConfirmarCorteRequestDto.LineaDto> ls = new ArrayList<>();
        for (int i = 0; i < montos.length; i++) {
            ConfirmarCorteRequestDto.LineaDto l = new ConfirmarCorteRequestDto.LineaDto();
            l.setIdlinea(c.getLineas().get(i).getIdlinea());
            l.setMontoProveedor(new BigDecimal(montos[i]));
            ls.add(l);
        }
        dto.setLineas(ls);
        dto.setComentario(comentario);
        return dto;
    }

    private void assertEstado(HttpStatus esperado, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    @Test
    @DisplayName("captura: la comisión es operaciones × comisión del servicio ($15 en pagos, 0 en recargas) y suma los totales")
    void calculaComision() {
        CorteServiciosResponseDto c = service.guardar(corte(linea(1, 40, "3200"), linea(2, 12, "4800.50")), "abigail");

        assertThat(c.getEstado()).isEqualTo((byte) 1);
        assertThat(c.getCodti()).isEqualTo(2);
        assertThat(c.getLineas().get(0).getComision()).isEqualByComparingTo("0");
        assertThat(c.getLineas().get(1).getComision()).isEqualByComparingTo("180");
        assertThat(c.getTotalMonto()).isEqualByComparingTo("8000.50");
        assertThat(c.getTotalComision()).isEqualByComparingTo("180");
        assertThat(c.getTotalProveedor()).isNull();
    }

    @Test
    @DisplayName("un servicio con comisión por operación exige las operaciones; uno desactivado, repetido o inexistente, 400; día futuro, 400")
    void validaciones() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.guardar(corte(linea(2, 0, "500")), "abigail"));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.guardar(corte(linea(3, 2, "500")), "abigail"));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.guardar(corte(linea(1, 1, "10"), linea(1, 1, "10")), "abigail"));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.guardar(corte(linea(99, 1, "10")), "abigail"));
        CorteServiciosRequestDto futuro = corte(linea(1, 1, "10"));
        futuro.setFecha(LocalDate.now().plusDays(1));
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.guardar(futuro, "abigail"));
        assertThat(cortes).isEmpty();
    }

    @Test
    @DisplayName("volver a capturar el mismo día reemplaza el corte mientras no esté confirmado")
    void reemplaza() {
        CorteServiciosResponseDto a = service.guardar(corte(linea(1, 10, "1000")), "abigail");
        CorteServiciosResponseDto b = service.guardar(corte(linea(1, 20, "2500"), linea(2, 3, "900")), "abigail");

        assertThat(b.getIdcorte()).isEqualTo(a.getIdcorte());
        assertThat(b.getLineas()).hasSize(2);
        assertThat(b.getTotalMonto()).isEqualByComparingTo("3400");
        assertThat(cortes).hasSize(1);
    }

    @Test
    @DisplayName("permisos: un vendedor no captura; un encargado solo su sucursal; un administrador cualquiera")
    void permisos() {
        assertEstado(HttpStatus.FORBIDDEN, () -> service.guardar(corte(linea(1, 1, "10")), "yamilet"));
        CorteServiciosRequestDto ajena = corte(linea(1, 1, "10"));
        ajena.setCodti(3);
        assertEstado(HttpStatus.FORBIDDEN, () -> service.guardar(ajena, "abigail"));
        assertThat(service.guardar(ajena, "root").getCodti()).isEqualTo(3);
    }

    @Test
    @DisplayName("confirmar sin diferencias: queda Confirmado; solo un administrador; y ya no se puede recapturar")
    void confirmarSinDiferencias() {
        CorteServiciosResponseDto c = service.guardar(corte(linea(1, 40, "3200"), linea(2, 12, "4800")), "abigail");

        assertEstado(HttpStatus.FORBIDDEN, () -> service.confirmar(c.getIdcorte(), confirmacion(c, null, "3200", "4800"), "abigail"));
        CorteServiciosResponseDto ok = service.confirmar(c.getIdcorte(), confirmacion(c, null, "3200", "4800"), "root");

        assertThat(ok.getEstado()).isEqualTo((byte) 2);
        assertThat(ok.getNombreConfirma()).isEqualTo("Root");
        assertThat(ok.getTotalDiferencia()).isEqualByComparingTo("0");
        assertEstado(HttpStatus.CONFLICT, () -> service.guardar(corte(linea(1, 1, "1")), "abigail"));
        assertEstado(HttpStatus.CONFLICT, () -> service.confirmar(c.getIdcorte(), confirmacion(c, null, "3200", "4800"), "root"));
    }

    @Test
    @DisplayName("confirmar con diferencias: exige el comentario y queda Con diferencias, con la diferencia por servicio")
    void confirmarConDiferencias() {
        CorteServiciosResponseDto c = service.guardar(corte(linea(1, 40, "3200"), linea(2, 12, "4800")), "abigail");

        assertEstado(HttpStatus.BAD_REQUEST, () -> service.confirmar(c.getIdcorte(), confirmacion(c, " ", "3200", "4750"), "root"));
        CorteServiciosResponseDto r = service.confirmar(c.getIdcorte(), confirmacion(c, "Falta un pago de luz", "3200", "4750"), "root");

        assertThat(r.getEstado()).isEqualTo((byte) 3);
        assertThat(r.getLineas().get(1).getDiferencia()).isEqualByComparingTo("50");
        assertThat(r.getTotalDiferencia()).isEqualByComparingTo("50");
        assertThat(r.getComentarioConfirmacion()).isEqualTo("Falta un pago de luz");
    }

    @Test
    @DisplayName("confirmar exige lo que reporta el proveedor de cada servicio y renglones del corte")
    void confirmarCompleto() {
        CorteServiciosResponseDto c = service.guardar(corte(linea(1, 40, "3200"), linea(2, 12, "4800")), "abigail");

        assertEstado(HttpStatus.BAD_REQUEST, () -> service.confirmar(c.getIdcorte(), confirmacion(c, null, "3200"), "root"));
        ConfirmarCorteRequestDto ajeno = confirmacion(c, null, "3200", "4800");
        ajeno.getLineas().get(1).setIdlinea(9999);
        assertEstado(HttpStatus.BAD_REQUEST, () -> service.confirmar(c.getIdcorte(), ajeno, "root"));
    }

    @Test
    @DisplayName("reabrir: solo un administrador; el corte vuelve a Capturado y se puede corregir")
    void reabrir() {
        CorteServiciosResponseDto c = service.guardar(corte(linea(1, 40, "3200")), "abigail");
        service.confirmar(c.getIdcorte(), confirmacion(c, null, "3200"), "root");

        assertEstado(HttpStatus.FORBIDDEN, () -> service.reabrir(c.getIdcorte(), "abigail"));
        CorteServiciosResponseDto r = service.reabrir(c.getIdcorte(), "root");
        assertThat(r.getEstado()).isEqualTo((byte) 1);
        assertThat(r.getTotalProveedor()).isNull();
        assertEstado(HttpStatus.CONFLICT, () -> service.reabrir(c.getIdcorte(), "root"));
        assertThat(service.guardar(corte(linea(1, 41, "3300")), "abigail").getTotalMonto()).isEqualByComparingTo("3300");
    }

    @Test
    @DisplayName("saldos del proveedor: el consumo (inicial + fondeo - final) se compara con lo operado")
    void saldos() {
        CorteServiciosRequestDto.LineaDto l = linea(1, 40, "3200");
        l.setSaldoInicial(new BigDecimal("10000"));
        l.setFondeo(new BigDecimal("2000"));
        l.setSaldoFinal(new BigDecimal("8750"));

        CorteServiciosResponseDto c = service.guardar(corte(l), "abigail");

        assertThat(c.getLineas().get(0).getConsumoSaldo()).isEqualByComparingTo("3250");
        assertThat(c.getLineas().get(0).getDiferenciaSaldo()).isEqualByComparingTo("50");
    }
}
