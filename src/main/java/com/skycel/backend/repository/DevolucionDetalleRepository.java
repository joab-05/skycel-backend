package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.DevolucionDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DevolucionDetalleRepository extends JpaRepository<DevolucionDetalle, Integer> {

    List<DevolucionDetalle> findByDevolucion_IddevolucionOrderByIddetalle(Integer iddevolucion);

    /** Lo ya devuelto (o en trámite) de un renglón de venta: pendientes y procesadas, no las rechazadas. */
    @Query("SELECT COALESCE(SUM(d.cantidad), 0) FROM DevolucionDetalle d WHERE d.iddetalleVenta = :iddetalleVenta " +
            "AND d.devolucion.estado IN (1, 2)")
    Long cantidadEnTramiteOProcesada(@Param("iddetalleVenta") Integer iddetalleVenta);
}
