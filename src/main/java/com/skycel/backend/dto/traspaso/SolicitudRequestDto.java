package com.skycel.backend.dto.traspaso;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SolicitudRequestDto {

    /** Tienda que pide. Opcional: si se omite es la del usuario. ROOT/ADMIN pueden pedir por cualquiera. */
    private Integer codtiOrigen;

    /** Tienda a la que se le pide (la que surte). */
    @NotNull(message = "La tienda a la que se le pide es obligatoria")
    private Integer codtiDestino;

    /** Productos pedidos, por su código en la tienda que surte. En equipos no se indican IMEI: los elige quien surte. */
    @NotEmpty(message = "La solicitud debe tener al menos un producto")
    @Valid
    private List<TraspasoLineaRequestDto> lineas;
}
