package com.skycel.backend.dto.producto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MovimientoInventarioResponseDto {
    private Long idmov;
    private LocalDateTime fecha;
    private String codpro;
    private String nombreProducto;
    /** ALTA, ENTRADA, SALIDA, AJUSTE, VENTA, CANCELACION_VENTA, TRASPASO_* */
    private String tipo;
    private String tipoDisplay;
    /** Con signo: positivo entra, negativo sale. */
    private BigDecimal cantidad;
    private BigDecimal stockAntes;
    private BigDecimal stockDespues;
    private String motivo;
    private String referencia;
    private String usuario;
}
