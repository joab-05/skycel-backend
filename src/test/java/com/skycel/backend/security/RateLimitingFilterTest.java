package com.skycel.backend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Límite de peticiones por IP: holgura para una tienda entera, freno al acceso y a la consulta pública. */
class RateLimitingFilterTest {

    private static MockHttpServletRequest peticion(String metodo, String ruta, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest(metodo, ruta);
        r.setRequestURI(ruta);
        r.setRemoteAddr(ip);
        return r;
    }

    /** Hace {@code n} peticiones y devuelve cuántas llegaron a la aplicación (no recibieron 429). */
    private static int pasan(RateLimitingFilter filtro, int n, String ruta, String ip) throws Exception {
        int pasaron = 0;
        FilterChain cadena = (req, res) -> { };
        for (int i = 0; i < n; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockHttpServletRequest req = peticion("GET", ruta, ip);
            if (filtro.shouldNotFilter(req)) { pasaron++; continue; }
            filtro.doFilter(req, res, cadena);
            if (res.getStatus() != 429) pasaron++;
        }
        return pasaron;
    }

    @Test
    void la_api_general_aguanta_300_por_minuto_y_despues_responde_429_con_json() throws Exception {
        RateLimitingFilter filtro = new RateLimitingFilter(300, 20, 15);

        assertThat(pasan(filtro, 300, "/api/productos", "10.0.0.1")).isEqualTo(300);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(peticion("GET", "/api/productos", "10.0.0.1"), res, (a, b) -> { });
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentType()).startsWith("application/json");
        assertThat(res.getContentAsString()).contains("\"status\":429").contains("description");
        assertThat(res.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void cada_ip_tiene_su_propia_cuenta() throws Exception {
        RateLimitingFilter filtro = new RateLimitingFilter(5, 20, 15);

        assertThat(pasan(filtro, 5, "/api/ventas", "10.0.0.1")).isEqualTo(5);
        assertThat(pasan(filtro, 1, "/api/ventas", "10.0.0.1")).isZero();
        assertThat(pasan(filtro, 5, "/api/ventas", "10.0.0.2")).isEqualTo(5);
    }

    @Test
    void el_inicio_de_sesion_tiene_un_limite_mas_estricto_y_aparte() throws Exception {
        RateLimitingFilter filtro = new RateLimitingFilter(300, 20, 15);

        assertThat(pasan(filtro, 30, "/api/auth/login", "10.0.0.1")).isEqualTo(20);
        // agotar el acceso no le quita nada a la API general de la misma IP
        assertThat(pasan(filtro, 10, "/api/productos", "10.0.0.1")).isEqualTo(10);
    }

    @Test
    void la_consulta_publica_por_folio_y_telefono_tiene_su_propio_limite() throws Exception {
        RateLimitingFilter filtro = new RateLimitingFilter(300, 20, 15);

        assertThat(pasan(filtro, 40, "/api/publico/ordenes-servicio/OS-000001", "10.0.0.1")).isEqualTo(15);
        assertThat(pasan(filtro, 10, "/api/ventas", "10.0.0.1")).isEqualTo(10);
    }

    @Test
    void la_aplicacion_web_y_la_prueba_de_salud_no_cuentan() throws Exception {
        RateLimitingFilter filtro = new RateLimitingFilter(3, 3, 3);

        assertThat(pasan(filtro, 500, "/app/js/app.js", "10.0.0.1")).isEqualTo(500);
        assertThat(pasan(filtro, 500, "/app/css/app.css", "10.0.0.1")).isEqualTo(500);
        assertThat(pasan(filtro, 500, "/api/publico/salud", "10.0.0.1")).isEqualTo(500);
        // y no gastaron la cuenta de la API
        assertThat(pasan(filtro, 3, "/api/productos", "10.0.0.1")).isEqualTo(3);
    }
}
