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

    /** Código del producto, ya existente en el catálogo de la sucursal que recibe. */
    private String codpro;

    private BigDecimal cantidad;
    private BigDecimal costoUnitario;

    private List<UnidadEquipoDTO> unidades;
}
