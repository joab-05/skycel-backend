package com.skycel.backend.dto.traspaso;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TraspasoResponseDto {
    private Integer idtraspaso;
    /** 1 = Envío, 2 = Solicitud */
    private Byte tipo;
    private String tipoDisplay;
    private Integer codtiOrigen;
    private String nombreTiendaOrigen;
    private Integer codtiDestino;
    private String nombreTiendaDestino;
    private Integer idusuarioCrea;
    private String nombreCrea;
    private Integer idusuarioValida;
    private String nombreValida;
    /** 1 Enviado, 2 Recibido, 3 Leído, 4 Aceptada, 5 Rechazada */
    private Byte estado;
    private String estadoDisplay;
    /** false = anulado */
    private Boolean activo;
    /** En un envío generado al aceptar una solicitud, el id de esa solicitud. */
    private Integer idtraspasoRef;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private String motivoRechazo;
    /** true si el envío se recibió con faltantes. */
    private Boolean conFaltantes;
    private String comentarioRecepcion;
    private List<TraspasoLineaResponseDto> lineas;
}
