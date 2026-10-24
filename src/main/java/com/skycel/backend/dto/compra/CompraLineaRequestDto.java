package com.skycel.backend.dto.compra;

import com.skycel.backend.domain.dto.request.UnidadEquipoDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una línea de la compra. Para un accesorio o servicio: {@code cantidad} + {@code costoUnitario}.
 * Para un celular o tablet (que se controlan por IMEI): {@code unidades}, una por cada equipo recibido,
 * con su propio costo — igual que al ajustar el stock de un equipo en Inventario.
 */
@Data
public class CompraLineaRequestDto {

    /** Código del producto (accesorios), ya existente en el catálogo de la sucursal que recibe. Un equipo no lleva código. */
    private String codpro;

    /** Equipos: el artículo recibido (existente o por categorías, como en el alta de equipos). */
    private Integer idprodmaster;
    private Short idCategoria;
    private String nombreProducto;
    private Short idColor;

    /** Equipos: precio de venta de las unidades que no traen el suyo. */
    private BigDecimal precioVenta;

    private BigDecimal cantidad;
    private BigDecimal costoUnitario;

    private List<UnidadEquipoDTO> unidades;
}
