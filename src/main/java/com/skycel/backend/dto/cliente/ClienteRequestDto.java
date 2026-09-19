package com.skycel.backend.dto.cliente;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ClienteRequestDto {
    private String     nombreCompleto; // obligatorio
    private String     telefono;
    private String     correo;
    private String     direccion;
    private Byte       tipoCliente;   // 1=Regular 2=Frecuente 3=VIP — default 1
    private BigDecimal margenFactor;
    private Integer    puntos;         // se puede ajustar manualmente
}