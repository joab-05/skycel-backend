package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.VentaDetalle;
import com.skycel.backend.dto.reporte.ResumenTiendaRow;
import com.skycel.backend.dto.reporte.TopProductoRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaDetalleRepository extends JpaRepository<VentaDetalle, Integer> {

    List<VentaDetalle> findByVenta_Idventa(Integer idventa);

    /**
     * Ventas, monto vendido y costo (para la utilidad) por sucursal, en un rango de fechas. Solo ventas
     * completadas (no canceladas); una sucursal sin ventas en el rango simplemente no aparece.
     */
    @Query("""
            SELECT new com.skycel.backend.dto.reporte.ResumenTiendaRow(
                v.tienda.codti, v.tienda.nombre, COUNT(DISTINCT v.idventa),
                COALESCE(SUM(d.precioUnitarioFinal * d.cantidad), 0), COALESCE(SUM(d.costoUnitarioCompra * d.cantidad), 0))
            FROM VentaDetalle d JOIN d.venta v
            WHERE v.estado = 1 AND v.fechaVenta BETWEEN :desde AND :hasta AND v.tienda.codti IN :codtis
            GROUP BY v.tienda.codti, v.tienda.nombre
            """)
    List<ResumenTiendaRow> resumenPorTienda(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                             @Param("codtis") List<Integer> codtis);

    /** Ventas, monto y costo de cada día del rango (solo ventas completadas). Filas: fecha, nº de ventas, monto, costo. */
    @Query("""
            SELECT CAST(v.fechaVenta AS LocalDate), COUNT(DISTINCT v.idventa),
                   COALESCE(SUM(d.precioUnitarioFinal * d.cantidad), 0), COALESCE(SUM(d.costoUnitarioCompra * d.cantidad), 0)
            FROM VentaDetalle d JOIN d.venta v
            WHERE v.estado = 1 AND v.fechaVenta BETWEEN :desde AND :hasta AND v.tienda.codti IN :codtis
            GROUP BY CAST(v.fechaVenta AS LocalDate)
            ORDER BY CAST(v.fechaVenta AS LocalDate)
            """)
    List<Object[]> ventasPorDia(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                @Param("codtis") List<Integer> codtis);

    /** Piezas, monto y costo por categoría del artículo. Filas: categoría, piezas, monto, costo. */
    @Query("""
            SELECT COALESCE(cat.nombre, 'Sin categoría'), COALESCE(SUM(d.cantidad), 0),
                   COALESCE(SUM(d.precioUnitarioFinal * d.cantidad), 0), COALESCE(SUM(d.costoUnitarioCompra * d.cantidad), 0)
            FROM VentaDetalle d JOIN d.venta v JOIN d.productoMaster pm LEFT JOIN pm.categoria cat
            WHERE v.estado = 1 AND v.fechaVenta BETWEEN :desde AND :hasta AND v.tienda.codti IN :codtis
            GROUP BY COALESCE(cat.nombre, 'Sin categoría')
            ORDER BY SUM(d.precioUnitarioFinal * d.cantidad) DESC
            """)
    List<Object[]> ventasPorCategoria(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                      @Param("codtis") List<Integer> codtis);

    /** Fecha de la última venta de cada artículo (código + sucursal) entre los pedidos. Filas: codpro, codti, fecha. */
    @Query("""
            SELECT d.codpro, v.tienda.codti, MAX(v.fechaVenta)
            FROM VentaDetalle d JOIN d.venta v
            WHERE v.estado = 1 AND d.codpro IN :codpros AND v.tienda.codti IN :codtis
            GROUP BY d.codpro, v.tienda.codti
            """)
    List<Object[]> ultimaVentaPorArticulo(@Param("codpros") List<String> codpros, @Param("codtis") List<Integer> codtis);

    /** Ranking de artículos por unidades vendidas, en el mismo rango y sucursales. */
    @Query("""
            SELECT new com.skycel.backend.dto.reporte.TopProductoRow(
                d.productoMaster.idprodmaster, d.productoMaster.nombreBase,
                SUM(d.cantidad), COALESCE(SUM(d.precioUnitarioFinal * d.cantidad), 0))
            FROM VentaDetalle d JOIN d.venta v
            WHERE v.estado = 1 AND v.fechaVenta BETWEEN :desde AND :hasta AND v.tienda.codti IN :codtis
            GROUP BY d.productoMaster.idprodmaster, d.productoMaster.nombreBase
            ORDER BY SUM(d.cantidad) DESC
            """)
    List<TopProductoRow> topPorCantidad(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                         @Param("codtis") List<Integer> codtis, Pageable limite);

    /** Ranking de artículos por monto vendido, en el mismo rango y sucursales. */
    @Query("""
            SELECT new com.skycel.backend.dto.reporte.TopProductoRow(
                d.productoMaster.idprodmaster, d.productoMaster.nombreBase,
                SUM(d.cantidad), COALESCE(SUM(d.precioUnitarioFinal * d.cantidad), 0))
            FROM VentaDetalle d JOIN d.venta v
            WHERE v.estado = 1 AND v.fechaVenta BETWEEN :desde AND :hasta AND v.tienda.codti IN :codtis
            GROUP BY d.productoMaster.idprodmaster, d.productoMaster.nombreBase
            ORDER BY SUM(d.precioUnitarioFinal * d.cantidad) DESC
            """)
    List<TopProductoRow> topPorMonto(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                      @Param("codtis") List<Integer> codtis, Pageable limite);

    /** Base para la comisión mensual de encargado: ventas NORMALES (sin PayJoy) de Equipo/Accesorio de su tienda,
     *  sin regalos ni líneas con precio distinto al del sistema (no deben incentivar ese tipo de excepción). */
    @Query("""
            SELECT COALESCE(SUM(d.precioUnitarioFinal * d.cantidad), 0)
            FROM VentaDetalle d JOIN d.venta v
            WHERE v.estado = 1 AND d.enganchePayjoy IS NULL AND v.tienda.codti = :codti
            AND v.fechaVenta BETWEEN :desde AND :hasta
            AND d.productoMaster.tipo <> com.skycel.backend.domain.enums.TipoProducto.SERVICIO
            AND (d.esRegalo IS NULL OR d.esRegalo = false)
            AND (d.precioAutorizado IS NULL OR d.precioAutorizado = false)
            """)
    java.math.BigDecimal baseComisionEncargado(@Param("codti") Integer codti,
                                                @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Lo devuelto (procesado) de esas mismas ventas, para restarlo de la base mientras el período siga pendiente. */
    @Query("""
            SELECT COALESCE(SUM(dd.cantidad * dd.precioUnitario), 0)
            FROM DevolucionDetalle dd
            WHERE dd.devolucion.estado = 2
            AND dd.iddetalleVenta IN (
                SELECT d.iddetalleVenta FROM VentaDetalle d JOIN d.venta v
                WHERE d.enganchePayjoy IS NULL AND v.tienda.codti = :codti AND v.fechaVenta BETWEEN :desde AND :hasta
            )
            """)
    java.math.BigDecimal devueltoComisionEncargado(@Param("codti") Integer codti,
                                                     @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Cada línea de equipo vendida a crédito PayJoy en el rango (quién la vendió y en cuánto), para calcular
     *  su comisión por tramo de precio. Ya excluye regalos y precio distinto al del sistema. */
    @Query("""
            SELECT new com.skycel.backend.dto.comision.VentaPayjoyLineaRow(
                v.usuarioVendedor.idusuario, v.usuarioVendedor.nombreCompleto, d.iddetalleVenta, d.precioUnitarioFinal)
            FROM VentaDetalle d JOIN d.venta v
            WHERE v.estado = 1 AND d.enganchePayjoy IS NOT NULL
            AND v.fechaVenta BETWEEN :desde AND :hasta
            AND (d.esRegalo IS NULL OR d.esRegalo = false)
            AND (d.precioAutorizado IS NULL OR d.precioAutorizado = false)
            """)
    List<com.skycel.backend.dto.comision.VentaPayjoyLineaRow> lineasPayjoy(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
