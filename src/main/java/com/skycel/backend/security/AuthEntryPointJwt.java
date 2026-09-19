package com.skycel.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skycel.backend.shared.dto.StandardApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    // ObjectMapper reutilizable para serializar la respuesta
    private final ObjectMapper objectMapper;

    public AuthEntryPointJwt() {
        this.objectMapper = new ObjectMapper();
        // Registrar módulo para manejar LocalDateTime correctamente
        this.objectMapper.registerModule(new JavaTimeModule());
        // IMPORTANTE: Desactivar serialización como timestamp (números)
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Configurar headers
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Construir respuesta estandarizada
        StandardApiResponse<Object> apiResponse = StandardApiResponse.builder()
                .success(false)
                .message("No autorizado: Se requiere autenticación para acceder a este recurso")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        // Escribir JSON en el response
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}