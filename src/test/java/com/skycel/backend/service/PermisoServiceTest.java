package com.skycel.backend.service;

import com.skycel.backend.domain.entity.PermisoRol;
import com.skycel.backend.domain.enums.Permiso;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.PermisoRolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Pruebas del catálogo de permisos y de su servicio (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class PermisoServiceTest {

    @Mock private PermisoRolRepository repo;
    private PermisoService service;
    /** Lo que "hay en la base": se llena con lo que el servicio guarda. */
    private final List<PermisoRol> base = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PermisoService(repo);
        lenient().when(repo.findAll()).thenAnswer(i -> new ArrayList<>(base));
        lenient().when(repo.save(any(PermisoRol.class))).thenAnswer(i -> { base.add(i.getArgument(0)); return i.getArgument(0); });
        lenient().doAnswer(i -> {
            String permiso = i.getArgument(0), marca = i.getArgument(1);
            base.removeIf(f -> f.getPermiso().equals(permiso) && !f.getRol().equals(marca));
            return null;
        }).when(repo).deleteByPermisoAndRolNot(any(), any());
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void comoRol(Rol rol) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "x", List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
    }

    @Test
    @DisplayName("administrar los permisos: solo se suma a quien root se lo dio; los demás administradores no lo tienen")
    void administrarPermisos_soloQuienRootAutorizo() {
        service.sembrar();
        var tienda = com.skycel.backend.domain.entity.Tienda.builder().codti(1).nombre("T").build();
        var joab = com.skycel.backend.domain.entity.Usuario.builder().username("joab").rol(Rol.ADMIN).tienda(tienda).administraPermisos(true).build();
        var otro = com.skycel.backend.domain.entity.Usuario.builder().username("otro").rol(Rol.ADMIN).tienda(tienda).build();

        assertThat(service.deUsuario(joab)).contains(PermisoService.PERMISOS_ADMIN);
        assertThat(service.deUsuario(otro)).doesNotContain(PermisoService.PERMISOS_ADMIN);
        assertThat(service.deRol(Rol.ADMIN)).doesNotContain(PermisoService.PERMISOS_ADMIN);
    }

    @Test
    @DisplayName("al sembrar, cada rol queda con lo que podía hacer antes; ROOT puede todo")
    void valoresIniciales() {
        service.sembrar();

        assertThat(service.tiene(Rol.ADMIN, "CAJA_CONFIG")).isTrue();
        assertThat(service.tiene(Rol.ENCARGADO_TIENDA, "CAJA_CONFIG")).isFalse();
        assertThat(service.tiene(Rol.ENCARGADO_TIENDA, "VENTA_CANCELAR")).isTrue();
        assertThat(service.tiene(Rol.VENDEDOR, "VENTA_CANCELAR")).isFalse();
        assertThat(service.tiene(Rol.VENDEDOR, "VENTA_CREAR")).isTrue();
        assertThat(service.tiene(Rol.VENDEDOR, "CAJA_OPERAR")).isTrue();
        assertThat(service.tiene(Rol.VENDEDOR, "CAJA_MOVIMIENTOS")).isFalse();
        assertThat(service.tiene(Rol.TECNICO, "TALLER_TRABAJAR")).isTrue();
        assertThat(service.tiene(Rol.TECNICO, "VENTA_CREAR")).isFalse();
        for (Permiso p : Permiso.values()) assertThat(service.tiene(Rol.ROOT, p.name())).as(p.name()).isTrue();
    }

    @Test
    @DisplayName("tiene(): usa el rol de quien hace la petición")
    void porPeticion() {
        service.sembrar();

        comoRol(Rol.VENDEDOR);
        assertThat(service.tiene("VENTA_CREAR")).isTrue();
        assertThat(service.tiene("VENTA_CANCELAR")).isFalse();
        assertThat(service.tiene("PERMISO_QUE_NO_EXISTE")).isFalse();
        comoRol(Rol.ROOT);
        assertThat(service.tiene("VENTA_CANCELAR")).isTrue();
        SecurityContextHolder.clearContext();
        assertThat(service.tiene("VENTA_CREAR")).as("sin sesión").isFalse();
    }

    @Test
    @DisplayName("root cambia un permiso y el cambio se aplica en seguida")
    void asignar() {
        service.sembrar();

        service.asignar("VENTA_CANCELAR", List.of("VENDEDOR"));

        assertThat(service.tiene(Rol.VENDEDOR, "VENTA_CANCELAR")).isTrue();
        assertThat(service.tiene(Rol.ENCARGADO_TIENDA, "VENTA_CANCELAR")).isFalse();
        assertThat(service.tiene(Rol.ADMIN, "VENTA_CANCELAR")).isFalse();
        assertThat(service.tiene(Rol.ROOT, "VENTA_CANCELAR")).isTrue();
    }

    @Test
    @DisplayName("lo que root cambió no se deshace al volver a arrancar, ni siquiera si dejó el permiso sin ningún rol")
    void sembrarNoPisaCambios() {
        service.sembrar();
        service.asignar("DESCUENTOS", List.of());
        service.asignar("CAJA_OPERAR", List.of("ADMIN"));

        PermisoService reinicio = new PermisoService(repo);
        reinicio.sembrar();

        assertThat(reinicio.tiene(Rol.ADMIN, "DESCUENTOS")).isFalse();
        assertThat(reinicio.tiene(Rol.VENDEDOR, "CAJA_OPERAR")).isFalse();
        assertThat(reinicio.tiene(Rol.ADMIN, "CAJA_OPERAR")).isTrue();
    }

    @Test
    @DisplayName("un permiso nuevo se siembra solo con sus roles iniciales y no toca los demás")
    void permisoNuevo() {
        service.sembrar();
        service.asignar("DESCUENTOS", List.of("VENDEDOR"));
        // simula que "COMPRAS" se agregó en una versión posterior: aún no tiene su marca ni sus roles
        base.removeIf(f -> f.getPermiso().equals("COMPRAS"));

        PermisoService reinicio = new PermisoService(repo);
        reinicio.sembrar();

        assertThat(reinicio.tiene(Rol.ENCARGADO_TIENDA, "COMPRAS")).isTrue();
        assertThat(reinicio.tiene(Rol.VENDEDOR, "DESCUENTOS")).isTrue();
    }

    @Test
    @DisplayName("no se puede configurar a ROOT ni usar roles o permisos que no existen")
    void validaciones() {
        service.sembrar();

        assertThatThrownBy(() -> service.asignar("VENTA_CREAR", List.of("ROOT")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.asignar("VENTA_CREAR", List.of("DIRECTOR")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.asignar("NO_EXISTE", List.of("ADMIN")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("el catálogo trae cada permiso con su etiqueta, su grupo y los roles que lo tienen")
    void catalogo() {
        service.sembrar();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> permisos = (List<Map<String, Object>>) service.catalogo().get("permisos");

        assertThat(permisos).hasSize(Permiso.values().length);
        Map<String, Object> venta = permisos.stream().filter(p -> p.get("codigo").equals("VENTA_CREAR")).findFirst().orElseThrow();
        assertThat(venta.get("etiqueta")).isEqualTo("Vender y cobrar");
        assertThat(venta.get("grupo")).isEqualTo("Ventas");
        assertThat(venta.get("roles")).isEqualTo(List.of("ADMIN", "ENCARGADO_TIENDA", "VENDEDOR"));
    }

    // ── Los controladores y el catálogo van de la mano ───────────────────────

    private static final Pattern USO = Pattern.compile("@permisos\\.tiene\\('([A-Z_]+)'\\)");

    private static Set<String> codigosUsadosEnControladores() throws IOException {
        Path dir = Path.of("src/main/java/com/skycel/backend/controller");
        Set<String> usados = new TreeSet<>();
        try (Stream<Path> archivos = Files.list(dir)) {
            for (Path f : archivos.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                Matcher m = USO.matcher(Files.readString(f));
                while (m.find()) usados.add(m.group(1));
            }
        }
        return usados;
    }

    @Test
    @DisplayName("todo permiso que usa un controlador existe en el catálogo (un error de dedo dejaría el endpoint cerrado para todos menos root)")
    void permisosDeLosControladoresExisten() throws IOException {
        Set<String> conocidos = Arrays.stream(Permiso.values()).map(Permiso::name).collect(Collectors.toSet());
        assertThat(codigosUsadosEnControladores()).isNotEmpty().isSubsetOf(conocidos);
    }

    @Test
    @DisplayName("todo permiso del catálogo protege algo (salvo los que usan solo el servidor interno o JSystem)")
    void permisosDelCatalogoSeUsan() throws IOException {
        Set<String> usados = codigosUsadosEnControladores();
        Set<String> sinEndpoint = Set.of("COSTOS_VER", "CONFIG_EQUIPO");   // COSTOS_VER lo consulta ProductoController; CONFIG_EQUIPO, JSystem
        List<String> huerfanos = Arrays.stream(Permiso.values()).map(Permiso::name)
                .filter(c -> !usados.contains(c) && !sinEndpoint.contains(c)).collect(Collectors.toList());
        assertThat(huerfanos).as("permisos que ningún endpoint usa").isEmpty();
    }
}
