package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.SesionCaja;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesionCajaRepository extends JpaRepository<SesionCaja, Integer> {

    /** La sesión abierta de una caja, si hay. */
    Optional<SesionCaja> findFirstByCaja_IdCajaAndEstado(Integer idCaja, Byte estado);

    /** El último turno cerrado de una caja (de ahí sale el fondo que se sugiere para abrir). */
    Optional<SesionCaja> findFirstByCaja_IdCajaAndEstadoOrderByFechaCierreDesc(Integer idCaja, Byte estado);

    boolean existsByCaja_IdCajaAndEstado(Integer idCaja, Byte estado);

    /** Historial de una caja, la más reciente primero. */
    List<SesionCaja> findByCaja_IdCajaOrderByFechaAperturaDesc(Integer idCaja, Pageable pageable);
}
