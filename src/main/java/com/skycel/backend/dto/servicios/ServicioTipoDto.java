package com.skycel.backend.dto.servicios;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Un tipo de servicio del corte diario: lo que responde el catálogo y lo que recibe al crear o editar. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicioTipoDto {

    private Integer idtipo;

    @NotBlank(message = "El nombre del servicio es obligatorio")
    @Size(max = 80, message = "El nombre no puede exceder los 80 caracteres")
    private String nombre;

    @NotNull(message = "La comisión por operación es obligatoria (0 si no se cobra)")
    @PositiveOrZero(message = "La comisión no puede ser negativa")
    @DecimalMax(value = "100000", message = "La comisión es demasiado alta")
    private BigDecimal comisionPorOperacion;

    /** Porcentaje de lo vendido que queda de ganancia (8.1 = 8.1 %). Opcional: 0. */
    @PositiveOrZero(message = "El porcentaje de ganancia no puede ser negativo")
    @DecimalMax(value = "100", message = "El porcentaje de ganancia no puede pasar de 100")
    private BigDecimal porcentajeGanancia;

    /** Lo que el proveedor cobra al negocio por operación (se resta de la comisión). Opcional: 0. */
    @PositiveOrZero(message = "El costo por operación no puede ser negativo")
    @DecimalMax(value = "100000", message = "El costo por operación es demasiado alto")
    private BigDecimal costoPorOperacion;

    /** true = solo se cuentan las operaciones, sin calcular ganancia (pagos PayJoy). Opcional: false. */
    private Boolean soloConteo;

    private Integer orden;

    private Boolean activo;
}
