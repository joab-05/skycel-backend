package com.skycel.backend.dto.traspaso;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class EnvioRequestDto {

    /** Tienda que envía. Opcional: si se omite es la del usuario. ROOT/ADMIN pueden enviar desde cualquiera. */
    private Integer codtiOrigen;

    @NotNull(message = "La tienda destino es obligatoria")
    private Integer codtiDestino;

    @NotEmpty(message = "El envío debe tener al menos un producto")
    @Valid
    private List<TraspasoLineaRequestDto> lineas;
}
