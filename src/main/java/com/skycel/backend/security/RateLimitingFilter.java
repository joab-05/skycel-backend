package com.skycel.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de peticiones por IP, con tres cubetas distintas:
 * <ul>
 *   <li><b>general</b> (por omisión 300 por minuto): toda la API. Una tienda entera sale por la misma IP pública, así que el
 *       límite tiene que dar holgura a varias PC de JSystem y varios celulares a la vez.</li>
 *   <li><b>acceso</b> (por omisión 20 por minuto): el inicio de sesión, para frenar a quien pruebe contraseñas.</li>
 *   <li><b>consulta pública</b> (por omisión 15 por minuto): la consulta de órdenes y garantías por folio + últimos 4
 *       dígitos del teléfono, que sin límite se podría adivinar probando los 10 mil números posibles.</li>
 * </ul>
 * No cuentan: los archivos de la aplicación web ({@code /app/**}, que el navegador guarda en caché), la prueba de salud que
 * JSystem hace cada 15 segundos y la documentación de la API.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    /** Tope de IP distintas que se recuerdan; al pasarlo se olvida todo (evita que el mapa crezca sin fin). */
    private static final int MAX_IPS = 20_000;

    private final Map<String, Bucket> general = new ConcurrentHashMap<>();
    private final Map<String, Bucket> acceso = new ConcurrentHashMap<>();
    private final Map<String, Bucket> consultaPublica = new ConcurrentHashMap<>();

    private final int limiteGeneral;
    private final int limiteAcceso;
    private final int limiteConsultaPublica;

    public RateLimitingFilter(
            @Value("${skycel.rate-limit.general-por-minuto:300}") int limiteGeneral,
            @Value("${skycel.rate-limit.acceso-por-minuto:20}") int limiteAcceso,
            @Value("${skycel.rate-limit.consulta-publica-por-minuto:15}") int limiteConsultaPublica) {
        this.limiteGeneral = limiteGeneral;
        this.limiteAcceso = limiteAcceso;
        this.limiteConsultaPublica = limiteConsultaPublica;
    }

    private static Bucket nuevaCubeta(int porMinuto) {
        Bandwidth limite = Bandwidth.builder().capacity(porMinuto).refillGreedy(porMinuto, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limite).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/app/")                 // la aplicación web (HTML, JS, CSS, íconos): son archivos estáticos
                || path.equals("/app")
                || path.equals("/favicon.ico")
                || path.equals("/api/publico/salud")    // la prueba de conexión que JSystem repite cada 15 s
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = getClientIP(request);

        Map<String, Bucket> mapa;
        int limite;
        if (path.equals("/api/auth/login")) {
            mapa = acceso;
            limite = limiteAcceso;
        } else if (path.startsWith("/api/publico/")) {
            mapa = consultaPublica;
            limite = limiteConsultaPublica;
        } else {
            mapa = general;
            limite = limiteGeneral;
        }
        if (mapa.size() > MAX_IPS) mapa.clear();
        Bucket cubeta = mapa.computeIfAbsent(ip, k -> nuevaCubeta(limite));

        if (cubeta.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "30");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":429,\"title\":\"Demasiadas peticiones\","
                    + "\"description\":\"Se hicieron demasiadas peticiones en poco tiempo. Espera unos segundos e intenta de nuevo.\"}");
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || !xfHeader.contains(request.getRemoteAddr())) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
