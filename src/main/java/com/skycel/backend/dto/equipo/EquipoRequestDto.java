package com.skycel.backend.dto.equipo;

import com.skycel.backend.domain.dto.request.UnidadEquipoDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alta de equipos (celulares o tablets) en una sucursal. El artículo es uno que ya existe ({@code idProductoMaster}) o se arma
 * con el árbol de categorías ({@code idCategoria} + {@code nombreProducto} + {@code idColor}), igual que un producto. Cada unidad
 * lleva su IMEI (en tablets, IMEI o número de serie); el costo y el precio de venta se piden aquí para todas, o por unidad.
 */
@Data
public class EquipoRequestDto {
    /** Sucursal donde quedan los equipos. Si se omite, la del usuario. */
    private Integer codti;

    /** Un artículo que ya existe. */
    private Integer idProductoMaster;

    /** O el artículo nuevo, por categorías: la subcategoría 1 o 2 (celular o tablet), el nombre y el color. */
    private Short idCategoria;
    private String nombreProducto;
    private Integer idDescripcion;
    private Short idColor;
    private Integer diasGarantia;

    /** Los equipos: IMEI o serie de cada uno, y si se quiere su condición, costo y precio propios. */
    private List<UnidadEquipoDTO> unidades;

    /** Costo y precio de venta que llevan las unidades que no traen los suyos. */
    private BigDecimal costo;
    private BigDecimal precioVenta;
    private Short idProveedor;
    private Short idSeccion;

    /** Cuántas unidades de este artículo se quieren tener como mínimo en la sucursal (aviso de reposición). Opcional. */
    private Integer stockMinimo;

    /** Para el historial de existencias (por ejemplo «Compra a Proveedor X», «Inventario inicial»). */
    private String motivo;
}
