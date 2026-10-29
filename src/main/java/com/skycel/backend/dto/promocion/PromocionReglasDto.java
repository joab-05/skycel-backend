package com.skycel.backend.dto.promocion;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** La promoción al vender un equipo como lista de reglas: lo que se lee y se guarda (se reemplaza la lista completa). */
@Data
public class PromocionReglasDto {

    /** Interruptor general: apagado, ninguna regla se aplica. */
    private Boolean activo;

    private List<ReglaDto> reglas;

    @Data
    public static class ReglaDto {
        /** Al guardar: el id de una regla existente (se actualiza); sin id, es una regla nueva. */
        private Integer id;
        private String  nombre;
        private Short   idCategoria;
        /** Nombre de la categoría, solo al leer. */
        private String  categoria;
        /** Rango del precio de lista (extremos incluidos); null = sin límite. */
        private BigDecimal precioDesde;
        private BigDecimal precioHasta;
        private Integer piezasPorEquipo;
        /** REGALO, PORCENTAJE, MONTO o PRECIO_FINAL */
        private String  tipo;
        private BigDecimal valor;
        /** NINGUNO, PESO_ARRIBA, PESO_ABAJO, CINCO_ARRIBA, CINCO_ABAJO, DIEZ_ARRIBA o DIEZ_ABAJO */
        private String  redondeo;
        private Boolean activa;
    }
}
