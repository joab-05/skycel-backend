package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.NominaDeduccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NominaDeduccionRepository extends JpaRepository<NominaDeduccion, Integer> {

    List<NominaDeduccion> findByDetalle_IddetalleOrderByIddeduccionAsc(Integer iddetalle);
}
