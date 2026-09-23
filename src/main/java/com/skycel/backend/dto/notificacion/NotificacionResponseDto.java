package com.skycel.backend.dto.notificacion;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificacionResponseDto {
    private Integer idnotificacion;
    private String  tipo;
    private String  titulo;
    private String  ruta;
    private Boolean leida;
    private LocalDateTime fechaCreacion;
}
