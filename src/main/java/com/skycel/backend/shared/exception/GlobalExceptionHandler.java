package com.skycel.backend.shared.exception;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    private final HttpServletResponse httpServletResponse;

    public GlobalExceptionHandler(HttpServletResponse httpServletResponse) {
        this.httpServletResponse = httpServletResponse;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "No autorizado");
        body.put("title", "Credenciales de acceso erroneas");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", "Usuario o contraseña no coinciden");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccountExpiredException.class)
    public ResponseEntity<Object> handleAccountExpiredException(AccountExpiredException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "No autorizado");
        body.put("title", "Cuenta de usuario expirada. ");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getMessage()+". Por favor contacte a soporte");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(CredentialsExpiredException.class)
    public ResponseEntity<Object> handleCredentialsExpiredException(CredentialsExpiredException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "No autorizado");
        body.put("title", "Credenciales de acceso expiradas");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getMessage()+". Por favor contacte a soporte");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Object> handleDisabledException(DisabledException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "No autorizado");
        body.put("title", "Cuenta de usuario deshabilitada");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getMessage()+". Por favor contacte a soporte");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Object> handleLockedException(LockedException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "No autorizado");
        body.put("title", "Cuenta de usuario bloqueada");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getMessage()+". Por favor contacte a soporte");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "No autorizado");
        body.put("title", "Requiere estar autenticado para acceder a este recurso");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getMessage()+". Por favor, ingresa tus credenciales");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    //.InsufficientAuthenticationException

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<Object> handleInsufficientAuthenticationException(InsufficientAuthenticationException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "No autorizado");
        body.put("title", "Requieres estar autenticado para acceder a este recurso");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getMessage()+". Por favor, ingresa con tus credenciales");
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Prohibido");
        body.put("title", "Acceso denegado");
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", "No estas autorizado para acceder a este recurso. "+ ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<Object> handleSignatureException(SignatureException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Prohibido");
        body.put("title", "Token JWT Invalido");
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", "El token JWT no se puede verificar "+ ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Object> handleExpiredJwtException(ExpiredJwtException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Prohibido");
        body.put("title", "Token JWT caducado");
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", "El token JWT que recibo ha expirado. "+ ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }


    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Object> handleRecursoNoEncontrado(
            RecursoNoEncontradoException ex,
            jakarta.servlet.http.HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Recurso No Encontrado");
        body.put("title", "El recurso solicitado no existe");
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getMessage());

        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<Object> handleRecursoDuplicado(
            RecursoDuplicadoException ex,
            jakarta.servlet.http.HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Conflicto de Datos");
        body.put("title", "Recurso duplicado");
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getMessage());

        log.warn("Intento de duplicado: {}", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    // IMPORTANTE: debe ir ANTES del catch-all de RuntimeException. ResponseStatusException
    // es la excepción que usan ProductoService/VentaService/etc. para todas sus validaciones
    // de negocio (400/404/409 con mensaje específico). Sin este handler dedicado, el
    // catch-all de abajo la interceptaba (por ser subclase de RuntimeException) y la
    // convertía en un 500 genérico, perdiendo el status code real y el mensaje.
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex,
            HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Error de Solicitud");
        body.put("title", ex.getReason() != null ? ex.getReason() : "Error al procesar la solicitud");
        body.put("status", ex.getStatusCode().value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", ex.getReason());

        log.warn("ResponseStatusException [{}]: {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    // Handler genérico para cualquier otra RuntimeException
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(
            RuntimeException ex,
            jakarta.servlet.http.HttpServletRequest request) {

        log.error("Error inesperado: ", ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "Error Interno");
        body.put("title", "Error en el servidor");
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("instance", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        body.put("description", "Ocurrió un error inesperado. Contacte al administrador.");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
