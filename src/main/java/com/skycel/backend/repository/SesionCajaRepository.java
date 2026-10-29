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

    Optional<SesionCaja> findFirstByClaveApertura(String claveApertura);

    Optional<SesionCaja> findFirstByClaveCierre(String claveCierre);

    /**
     * Los cortes cerrados en ese rango con faltante (efectivo contado menor al esperado) que todavía no se descuentan a nadie
     * en la nómina, del más antiguo al más reciente.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT s FROM SesionCaja s
            WHERE s.estado = 2 AND s.diferencia < 0 AND s.faltanteNomina IS NULL
              AND s.fechaCierre >= :desde AND s.fechaCierre < :hasta
            ORDER BY s.fechaCierre ASC
            """)
    List<SesionCaja> faltantesPorDescontar(@org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde,
                                           @org.springframework.data.repository.query.Param("hasta") java.time.LocalDateTime hasta);

    /** Historial de una caja, la más reciente primero. */
    List<SesionCaja> findByCaja_IdCajaOrderByFechaAperturaDesc(Integer idCaja, Pageable pageable);
}
