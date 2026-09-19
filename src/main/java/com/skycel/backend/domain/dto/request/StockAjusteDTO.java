package com.skycel.backend.domain.dto.request;

import lombok.Data;
import java.math.BigDecimal;

/**
 * PATCH /api/productos/{codpro}/stock
 * Registra un movimiento de inventario.
 */
@Data
public class StockAjusteDTO {

    /**
     * ENTRADA  → suma al stock actual
     * SALIDA   → resta del stock actual
     * AJUSTE   → establece el valor exacto
     */
    private String tipo;               // "ENTRADA" | "SALIDA" | "AJUSTE"

    private BigDecimal cantidad;       // unidades del movimiento

    private String comentario;         // motivo del ajuste

    private java.util.List<String> imeis; // Opcional. Lista de IMEIs si el producto es CELULAR.
}