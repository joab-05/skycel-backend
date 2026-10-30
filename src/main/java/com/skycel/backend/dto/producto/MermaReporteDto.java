package com.skycel.backend.dto.producto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Control de mermas: los cambios de existencias que no son una venta, una devolución ni un traspaso (salidas y ajustes manuales,
 * bajas de equipos y ajustes de inventario físico), con quién los hizo, por qué y cuánto valen a costo.
 */
@Data
@Builder
public class MermaReporteDto {

    private LocalDateTime desde;
    private LocalDateTime hasta;
    /** null = todas las tiendas (solo administrador). */
    private Integer codti;
    /** false si quien consulta no puede ver costos: los valores van vacíos. */
    private boolean conCostos;
    private int movimientos;
    /** Lo que salió de las existencias, a costo (positivo). */
    private BigDecimal perdida;
    /** Lo que entró por ajustes al alza, a costo (positivo). */
    private BigDecimal sobrante;
    private List<ResumenDto> porCategoria;
    private List<ResumenDto> porUsuario;
    private List<ResumenDto> porTienda;
    private List<MovimientoDto> detalle;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumenDto {
        private String nombre;
        private int movimientos;
        /** Unidades netas (negativo = salieron). */
        private BigDecimal unidades;
        /** Valor neto a costo (negativo = pérdida); null sin permiso de costos. */
        private BigDecimal valor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovimientoDto {
        private Long idmov;
        private LocalDateTime fecha;
        private Integer codti;
        private String tienda;
        private String usuario;
        /** SALIDA o AJUSTE. */
        private String tipo;
        private String categoria;
        private String articulo;
        private String codigo;
        private String imei;
        /** Con signo: negativo salió, positivo entró. */
        private BigDecimal cantidad;
        private BigDecimal valor;
        private String motivo;
        private String referencia;
    }
}
