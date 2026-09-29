package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.NominaPercepcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NominaPercepcionRepository extends JpaRepository<NominaPercepcion, Integer> {

    List<NominaPercepcion> findByDetalle_IddetalleOrderByIdpercepcionAsc(Integer iddetalle);
}
