package com.skycel.backend.dto.garantia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Recepción de un producto que el cliente reclama por garantía. */
@Data
public class GarantiaRequestDto {

    /** Sucursal donde se recibe. Opcional: si se omite es la del usuario. */
    private Integer codti;

    /** La venta original del producto. */
    @NotNull(message = "La venta es obligatoria")
    private Integer idventa;

    /** Equipos (celular o tablet): el IMEI o número de serie que aparece en la venta. */
    @Size(max = 20, message = "El IMEI o serie no puede exceder los 20 caracteres")
    private String imei;

    /** Accesorios: el código del producto que aparece en la venta. Se indica este o el IMEI. */
    @Size(max = 25, message = "El código no puede exceder los 25 caracteres")
    private String codpro;

    @NotBlank(message = "La falla reportada es obligatoria")
    private String fallaReportada;

    /** Golpes, rayones o cualquier detalle del estado en que se recibe. */
    private String diagnosticoInicial;

    /**
     * A quién avisarle. Si la venta tiene cliente se toma de él; hay que indicarlos cuando la venta no tenía cliente
     * (o para avisarle a otra persona).
     */
    @Size(max = 150, message = "El nombre no puede exceder los 150 caracteres")
    private String nombreContacto;

    @Size(max = 20, message = "El teléfono no puede exceder los 20 caracteres")
    private String telefonoContacto;
}
