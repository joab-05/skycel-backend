package com.skycel.backend.dto.equipo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** La historia completa de un equipo: dónde está hoy y, en orden, todo lo que le ha pasado desde que entró. */
@Data
@Builder
public class RastroEquipoDto {

    /** El equipo como está ahora (estado, sucursal, precio...). */
    private EquipoResponseDto equipo;

    /** Lo que le ha pasado, del más antiguo al más reciente. */
    private List<EventoDto> eventos;

    /** Cuándo entró al sistema. */
    private LocalDateTime ingreso;
    /** Días que estuvo (o lleva) en inventario: hasta su venta, o hasta hoy si todavía no se vende. */
    private Long diasEnInventario;
    /** Las sucursales por las que pasó, en orden. */
    private List<String> recorrido;
    private int traspasos;
    private int garantias;
    private boolean vendido;

    @Data
    @Builder
    public static class EventoDto {
        private LocalDateTime fecha;
        /** El tipo del movimiento (ALTA, TRASPASO_SALIDA, VENTA, GARANTIA...). */
        private String tipo;
        /** Lo que pasó, en una frase («Salió de Bodega central»). */
        private String titulo;
        /** El detalle: a dónde fue, a quién se vendió, el motivo... */
        private String detalle;
        private Integer codti;
        private String tienda;
        private String usuario;
        /** La operación de donde viene: «Venta #12», «Traspaso #16», el folio de una garantía... */
        private String referencia;
        /** Un importe, si aplica (el precio al que se vendió). */
        private BigDecimal monto;
    }
}
