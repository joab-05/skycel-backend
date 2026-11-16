package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.NominaDeduccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NominaDeduccionRepository extends JpaRepository<NominaDeduccion, Integer> {

    List<NominaDeduccion> findByDetalle_IddetalleOrderByIddeduccionAsc(Integer iddetalle);

    /** La deducción que cobró el faltante de un corte de caja (si ya se descontó). */
    List<NominaDeduccion> findBySesion_IdsesionCaja(Integer idsesionCaja);
}
