package com.skycel.backend.dto.garantia;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cambio de estado de una garantía. */
@Data
public class AvanceGarantiaRequestDto {

    /**
     * Nuevo estado: 2 En tránsito a bodega, 3 Recibido en bodega, 4 En tránsito al proveedor, 5 En el proveedor,
     * 6 Reparado por el proveedor, 7 Cambio físico, 8 Rechazado, 9 Viaje de retorno, 10 Listo para entrega, 11 Entregado.
     */
    @NotNull(message = "El nuevo estado es obligatorio")
    private Byte estado;

    /** Obligatorio al resolver (6 Reparado, 7 Cambio físico u 8 Rechazado): qué se hizo o por qué se rechazó. */
    private String comentario;

    /** Solo en un cambio físico (7): el IMEI o serie del equipo nuevo. */
    @Size(max = 20, message = "El IMEI o serie no puede exceder los 20 caracteres")
    private String imeiReemplazo;
}
