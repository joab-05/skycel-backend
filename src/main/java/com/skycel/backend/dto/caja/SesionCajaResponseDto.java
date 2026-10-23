package com.skycel.backend.dto.caja;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Un turno de caja: abierto (con el resumen en vivo) o cerrado (con el corte guardado). */
@Data
@Builder
public class SesionCajaResponseDto {

    /** null cuando la caja está cerrada y se consulta "la sesión actual". */
    private Integer idsesionCaja;
    private Integer idCaja;
    private String  nombreCaja;
    private Integer codti;
    private String  nombreTienda;

    /** true si hay un turno abierto. */
    private boolean abierta;
    private String  usuarioApertura;
    private String  usuarioCierre;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;

    private BigDecimal fondoInicial;
    /** Abierta: lo que debe haber en el cajón ahora. Cerrada: lo que debía haber al cerrar. */
    private BigDecimal efectivoEsperado;
    private BigDecimal efectivoContado;
    /** Contado menos esperado (negativo = faltante). */
    private BigDecimal diferencia;
    private BigDecimal fondoSiguiente;
    private BigDecimal retiroCierre;
    private String observaciones;

    /** Cuando la caja está cerrada: con cuánto se sugiere abrir (lo que quedó en el cajón). */
    private BigDecimal fondoSugerido;

    /** true si después del cierre entraron movimientos con fecha del turno (p. ej. ventas hechas sin conexión). */
    private boolean modificadaDespuesDelCierre;

    private ResumenDto resumen;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumenDto {
        private int        numVentas;
        private BigDecimal totalVentas;
        private int        ventasCanceladas;
        private List<MetodoDto> ventasPorMetodo;
        private int        numAbonos;
        private BigDecimal totalAbonos;
        /** Entradas del turno sin contar el fondo (ventas en efectivo, abonos, otros ingresos...). */
        private BigDecimal totalEntradas;
        /** Salidas del turno sin contar el retiro del cierre. */
        private BigDecimal totalSalidas;
        private List<MotivoDto> movimientos;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetodoDto {
        private String     metodo;
        private int        cantidad;
        private BigDecimal total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MotivoDto {
        private String     motivo;
        /** 1 entrada, 2 salida. */
        private int        tipo;
        private int        cantidad;
        private BigDecimal total;
    }
}
