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

    private Integer orden;

    private Boolean activo;
}
