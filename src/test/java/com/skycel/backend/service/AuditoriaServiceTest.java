package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del cálculo puro de diferencias entre dos capturas de un registro (sin Spring, sin Envers, sin
 * base de datos): construye objetos reales con Builder y compara. Envers y la consulta de revisiones se
 * verificaron por API contra el servidor de prueba, no aquí (no hay infraestructura de pruebas con Spring
 * en este proyecto para eso, ver las demás *ServiceTest).
 */
class AuditoriaServiceTest {

    @Test
    @DisplayName("detecta el campo que cambió y lo que valía antes y después")
    void detectaCambioSimple() {
        Producto antes = Producto.builder().idproducto(1).codpro("X").preciopub(new BigDecimal("100.00")).build();
        Producto despues = Producto.builder().idproducto(1).codpro("X").preciopub(new BigDecimal("120.00")).build();

        List<Map<String, Object>> cambios = AuditoriaService.diferencias(antes, despues, Producto.class);

        assertThat(cambios).hasSize(1);
        assertThat(cambios.get(0).get("campo")).isEqualTo("preciopub");
        assertThat(cambios.get(0).get("antes")).isEqualTo(new BigDecimal("100.00"));
        assertThat(cambios.get(0).get("despues")).isEqualTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("un BigDecimal con distinta escala pero el mismo valor (100 vs 100.00) no es un cambio")
    void bigDecimalMismoValorDistintaEscala_noEsCambio() {
        Producto antes = Producto.builder().idproducto(1).preciopub(new BigDecimal("100")).build();
        Producto despues = Producto.builder().idproducto(1).preciopub(new BigDecimal("100.00")).build();

        assertThat(AuditoriaService.diferencias(antes, despues, Producto.class)).isEmpty();
    }

    @Test
    @DisplayName("varios campos cambiados: se reportan todos")
    void variosCambios_seReportanTodos() {
        Producto antes = Producto.builder().idproducto(1).codpro("X").stock(new BigDecimal("5")).preciopub(new BigDecimal("10")).activo(true).build();
        Producto despues = Producto.builder().idproducto(1).codpro("X").stock(new BigDecimal("8")).preciopub(new BigDecimal("12")).activo(false).build();

        List<Map<String, Object>> cambios = AuditoriaService.diferencias(antes, despues, Producto.class);

        assertThat(cambios).extracting(m -> m.get("campo")).containsExactlyInAnyOrder("stock", "preciopub", "activo");
    }

    @Test
    @DisplayName("nada cambió: no hay diferencias")
    void sinCambios_listaVacia() {
        Producto antes = Producto.builder().idproducto(1).codpro("X").preciopub(new BigDecimal("10")).build();
        Producto despues = Producto.builder().idproducto(1).codpro("X").preciopub(new BigDecimal("10")).build();

        assertThat(AuditoriaService.diferencias(antes, despues, Producto.class)).isEmpty();
    }

    @Test
    @DisplayName("la contraseña de un usuario nunca aparece, aunque cambie")
    void passwordNuncaSeMuestra() {
        Usuario antes = Usuario.builder().idusuario(1).username("ana").password("hash-viejo").rol(Rol.VENDEDOR).build();
        Usuario despues = Usuario.builder().idusuario(1).username("ana").password("hash-nuevo").rol(Rol.ENCARGADO_TIENDA).build();

        List<Map<String, Object>> cambios = AuditoriaService.diferencias(antes, despues, Usuario.class);

        assertThat(cambios).extracting(m -> m.get("campo")).doesNotContain("password").contains("rol");
    }

    @Test
    @DisplayName("el número de versión (bloqueo optimista) nunca se muestra")
    void versionNuncaSeMuestra() {
        Tienda antes = Tienda.builder().codti(1).nombre("Matriz").build();
        Tienda despues = Tienda.builder().codti(1).nombre("Matriz Centro").build();
        // version es un campo heredado de otras entidades; Tienda no lo tiene, pero probamos con Usuario que sí
        Usuario a = Usuario.builder().idusuario(1).username("ana").version(1).build();
        Usuario d = Usuario.builder().idusuario(1).username("ana").version(2).build();

        assertThat(AuditoriaService.diferencias(a, d, Usuario.class)).isEmpty();
        assertThat(AuditoriaService.diferencias(antes, despues, Tienda.class))
                .extracting(m -> m.get("campo")).containsExactly("nombre");
    }

    @Test
    @DisplayName("las fechas de auditoría (LocalDateTime) no se muestran: ya las cuenta la revisión")
    void fechasNoSeMuestran() {
        Usuario antes = Usuario.builder().idusuario(1).username("ana").fechaAlta(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        Usuario despues = Usuario.builder().idusuario(1).username("ana").fechaAlta(LocalDateTime.of(2026, 6, 1, 0, 0)).build();

        assertThat(AuditoriaService.diferencias(antes, despues, Usuario.class)).isEmpty();
    }

    @Test
    @DisplayName("las relaciones (@ManyToOne) no se comparan, para no arriesgar cargar algo fuera de sesión")
    void relacionesNoSeComparan() {
        Tienda t1 = Tienda.builder().codti(1).nombre("Matriz").build();
        Tienda t2 = Tienda.builder().codti(2).nombre("Zócalo").build();
        Usuario antes = Usuario.builder().idusuario(1).username("ana").tienda(t1).build();
        Usuario despues = Usuario.builder().idusuario(1).username("ana").tienda(t2).build();

        assertThat(AuditoriaService.diferencias(antes, despues, Usuario.class)).isEmpty();
    }

    @Test
    @DisplayName("sin una de las dos capturas (alta o baja), no hay diferencias que calcular")
    void sinAlgunaCaptura_listaVacia() {
        Producto p = Producto.builder().idproducto(1).build();
        assertThat(AuditoriaService.diferencias(null, p, Producto.class)).isEmpty();
        assertThat(AuditoriaService.diferencias(p, null, Producto.class)).isEmpty();
    }
}
