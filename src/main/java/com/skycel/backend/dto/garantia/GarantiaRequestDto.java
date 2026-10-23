package com.skycel.backend.dto.garantia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Recepción de un producto que el cliente reclama por garantía. */
@Data
public class GarantiaRequestDto {

    /** Sucursal donde se recibe. Opcional: si se omite es la del usuario. */
    private Integer codti;

    /** La venta original del producto. Obligatoria, salvo que el producto se haya vendido con el sistema anterior. */
    private Integer idventa;

    /**
     * true si el producto se vendió antes de este sistema (no hay venta aquí). Entonces son obligatorios
     * {@code nombreProducto}, {@code fechaCompra} y {@code diasGarantia}; el resto de datos del producto es opcional.
     */
    private Boolean ventaAnterior;

    @Size(max = 255, message = "El nombre del producto no puede exceder los 255 caracteres")
    private String nombreProducto;

    /** CELULAR (equipo) o ACCESORIO; si se omite, es CELULAR cuando viene IMEI y ACCESORIO si no. */
    @Size(max = 20, message = "El tipo no puede exceder los 20 caracteres")
    private String tipoProducto;

    /** Cuándo lo compró el cliente (la fecha del ticket viejo). */
    private java.time.LocalDate fechaCompra;

    /** Días de garantía que tenía el producto al venderse. */
    private Integer diasGarantia;

    /** Número de ticket o venta del sistema anterior, si el cliente lo trae. */
    @Size(max = 40, message = "El folio anterior no puede exceder los 40 caracteres")
    private String folioVentaAnterior;

    /** Proveedor al que se manda a garantía, si se sabe (con venta de este sistema sale del producto). */
    private Short idProveedor;

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
