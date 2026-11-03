package com.skycel.backend.security;

import com.skycel.backend.service.AntiDobleEnvio;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Candado contra el doble clic para toda la API: si el mismo usuario manda dos veces la misma operación (mismo método, ruta y
 * cuerpo) con menos de 3 segundos de diferencia, la segunda se rechaza con 409. Sin esto, dos clics seguidos llegaban juntos y
 * MySQL abortaba uno por «deadlock» (o, en acciones que no tocan inventario, las dos se registraban).
 *
 * <p>Si la primera falla (4xx/5xx) la firma se libera para poder reintentar enseguida. No se aplica a lo que ya es idempotente por
 * su propia clave ({@code claveOffline}, que reenvía la cola sin conexión), a las lecturas, al inicio de sesión ni a las subidas de
 * archivos.
 */
public class AntiDobleClicFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS = Set.of("POST", "PUT", "PATCH", "DELETE");
    /** Cuerpos más grandes (fotos, logos) no se firman: no son clics repetidos de un formulario. */
    private static final int MAX_CUERPO = 100_000;

    private final AntiDobleEnvio candado = new AntiDobleEnvio();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!METODOS.contains(request.getMethod())) return true;
        if (path.startsWith("/api/auth/") || path.startsWith("/api/publico/")) return true;
        String tipo = request.getContentType();
        if (tipo != null && !tipo.toLowerCase().contains("json")) return true;
        return request.getContentLengthLong() > MAX_CUERPO;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }
        byte[] cuerpo = request.getInputStream().readNBytes(MAX_CUERPO + 1);
        HttpServletRequest repetible = new CuerpoRepetible(request, cuerpo);
        String texto = new String(cuerpo, StandardCharsets.UTF_8);
        if (cuerpo.length > MAX_CUERPO || texto.contains("claveOffline")) {
            chain.doFilter(repetible, response);
            return;
        }

        String firma = auth.getName() + "|" + request.getMethod() + "|" + request.getRequestURI() + "?" + request.getQueryString()
                + "|" + sha256(cuerpo);
        if (!candado.intentar(firma, null)) {
            response.setStatus(HttpStatus.CONFLICT.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":409,\"title\":\"Operación repetida\","
                    + "\"description\":\"Esta operación ya se registró hace un momento: revisa antes de repetirla.\"}");
            return;
        }
        try {
            chain.doFilter(repetible, response);
        } finally {
            if (response.getStatus() >= 400) candado.liberar(firma);
        }
    }

    private static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** La petición con su cuerpo ya leído, para que el controlador pueda leerlo otra vez. */
    private static final class CuerpoRepetible extends HttpServletRequestWrapper {
        private final byte[] cuerpo;

        CuerpoRepetible(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { /* lectura síncrona */ }
            };
        }

        @Override
        public BufferedReader getReader() {
            String cs = getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8";
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cuerpo), java.nio.charset.Charset.forName(cs)));
        }
    }
}
