package com.skycel.backend.dto.cliente;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ClienteResponseDto {
    private Integer       idcliente;
    private String        nombreCompleto;
    private String        telefono;
    private String        correo;
    private String        direccion;
    private Byte          tipoCliente;
    private String        tipoClienteDisplay; // "Regular", "Frecuente", "VIP"
    private String        tipoColor;          // color para el badge
    private BigDecimal    margenFactor;
    private Integer       puntos;
    private Long          totalCompras;       // calculado desde venta
    private BigDecimal    totalGastado;       // calculado desde venta
    private LocalDateTime fechaRegistro;
    private Boolean       activo;
}