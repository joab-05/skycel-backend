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
    /** Solo un TECNICO: es el técnico encargado. */
    private Boolean tecnicoEncargado;
    /** Los permisos de su rol (ver Permiso): JSystem y la web muestran u ocultan secciones y botones con ellos. */
    private java.util.List<String> permisos;
}