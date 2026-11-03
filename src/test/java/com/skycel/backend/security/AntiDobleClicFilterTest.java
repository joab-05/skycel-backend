package com.skycel.backend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Filtro contra el doble clic")
class AntiDobleClicFilterTest {

    private AntiDobleClicFilter filtro;
    private AtomicInteger llegaron;
    private int estadoDelControlador;

    @BeforeEach
    void preparar() {
        filtro = new AntiDobleClicFilter();
        llegaron = new AtomicInteger();
        estadoDelControlador = 200;
        entra("abigail");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void entra(String usuario) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario, "x", List.of()));
    }

    private MockHttpServletResponse manda(String metodo, String ruta, String cuerpo) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, ruta);
        req.setContentType("application/json");
        req.setContent(cuerpo.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain cadena = (rq, rs) -> {
            // el controlador debe poder leer el cuerpo que el filtro ya leyó
            assertThat(new String(rq.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(cuerpo);
            llegaron.incrementAndGet();
            ((jakarta.servlet.http.HttpServletResponse) rs).setStatus(estadoDelControlador);
        };
        filtro.doFilter(req, res, cadena);
        return res;
    }

    @Test
    @DisplayName("la misma operación dos veces seguidas: la segunda se rechaza con 409 y no llega al controlador")
    void dobleClic() throws Exception {
        assertThat(manda("POST", "/api/ordenes-servicio/2/entregar", "{\"metodoPago\":1}").getStatus()).isEqualTo(200);

        MockHttpServletResponse segunda = manda("POST", "/api/ordenes-servicio/2/entregar", "{\"metodoPago\":1}");

        assertThat(segunda.getStatus()).isEqualTo(409);
        assertThat(segunda.getContentAsString(StandardCharsets.UTF_8)).contains("ya se registró");
        assertThat(llegaron.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("otro cuerpo, otra ruta u otro usuario no son un doble clic")
    void distintas() throws Exception {
        manda("POST", "/api/traspasos/4/recibir", "{\"a\":1}");

        assertThat(manda("POST", "/api/traspasos/4/recibir", "{\"a\":2}").getStatus()).isEqualTo(200);
        assertThat(manda("POST", "/api/traspasos/5/recibir", "{\"a\":1}").getStatus()).isEqualTo(200);
        entra("miguel");
        assertThat(manda("POST", "/api/traspasos/4/recibir", "{\"a\":1}").getStatus()).isEqualTo(200);
        assertThat(llegaron.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("si la primera falla se puede reintentar enseguida")
    void reintentoTrasFallo() throws Exception {
        estadoDelControlador = 400;
        manda("POST", "/api/ventas/9/cancelar", "{}");

        estadoDelControlador = 200;
        assertThat(manda("POST", "/api/ventas/9/cancelar", "{}").getStatus()).isEqualTo(200);
        assertThat(llegaron.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("lo que trae su propia claveOffline, las lecturas y el inicio de sesión no se filtran")
    void excepciones() throws Exception {
        manda("POST", "/api/ventas", "{\"claveOffline\":\"abc\"}");
        manda("POST", "/api/ventas", "{\"claveOffline\":\"abc\"}");
        manda("GET", "/api/productos/tienda/2", "");
        manda("GET", "/api/productos/tienda/2", "");
        manda("POST", "/api/auth/login", "{\"u\":1}");
        manda("POST", "/api/auth/login", "{\"u\":1}");

        assertThat(llegaron.get()).isEqualTo(6);
    }

    @Test
    @DisplayName("sin sesión no se filtra (la seguridad ya la rechaza)")
    void sinSesion() throws Exception {
        SecurityContextHolder.clearContext();
        manda("POST", "/api/ventas/1/cancelar", "{}");
        manda("POST", "/api/ventas/1/cancelar", "{}");

        assertThat(llegaron.get()).isEqualTo(2);
    }
}
