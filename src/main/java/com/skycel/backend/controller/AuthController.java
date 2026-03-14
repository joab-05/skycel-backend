package com.skycel.backend.controller;

import com.skycel.backend.dto.auth.LoginRequestDto;
import com.skycel.backend.dto.auth.LoginResponseDto;
import com.skycel.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para inicio y cierre de sesión de usuarios")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar Sesión", description = "Autentica credenciales contra la base de datos y genera el token JWT")
    public ResponseEntity<LoginResponseDto> login(
            @Valid @RequestBody LoginRequestDto requestDto,
            HttpServletRequest request) {
        
        LoginResponseDto response = authService.authenticate(requestDto, request);
        return ResponseEntity.ok(response);
    }
}
