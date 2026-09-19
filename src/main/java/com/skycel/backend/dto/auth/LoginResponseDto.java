package com.skycel.backend.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseDto {
    private String token;
    private Integer idusuario;
    private String username;
    private String nombreCompleto;
    private String rol;
    private Integer codti;
}