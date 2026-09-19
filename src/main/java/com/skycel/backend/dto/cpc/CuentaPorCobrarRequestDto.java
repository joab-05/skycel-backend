// ── CuentaPorCobrarRequestDto.java ───────────────────────────────────────────
package com.skycel.backend.dto.cpc;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CuentaPorCobrarRequestDto {
    private String     noFactura;        // obligatorio, único
    private Integer    idcliente;        // obligatorio
    private Integer    idventa;          // opcional
    private LocalDate  fechaEmision;
    private LocalDate  fechaVencimiento; // obligatorio
    private BigDecimal montoTotal;       // obligatorio
    private String     observaciones;
}