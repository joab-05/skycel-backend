package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.NominaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NominaDetalleRepository extends JpaRepository<NominaDetalle, Integer> {

    List<NominaDetalle> findByPeriodo_IdperiodoOrderByIddetalleAsc(Integer idperiodo);

    Optional<NominaDetalle> findByPeriodo_IdperiodoAndEmpleado_Idempleado(Integer idperiodo, Integer idempleado);

    List<NominaDetalle> findByEmpleado_IdempleadoOrderByPeriodo_FechaInicioDesc(Integer idempleado);

    long countByPeriodo_IdperiodoAndEstado(Integer idperiodo, Byte estado);

    /** Los pagos de nómina ya hechos en un periodo (por la fecha en que se pagaron). */
    List<NominaDetalle> findByEstadoAndFechaPagoBetweenOrderByFechaPagoAsc(Byte estado, java.time.LocalDateTime desde, java.time.LocalDateTime hasta);
}
