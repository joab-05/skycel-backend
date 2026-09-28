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
}
