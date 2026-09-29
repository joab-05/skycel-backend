package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.NominaPeriodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NominaPeriodoRepository extends JpaRepository<NominaPeriodo, Integer> {

    Optional<NominaPeriodo> findFirstByEstadoOrderByFechaInicioDesc(Byte estado);

    List<NominaPeriodo> findAllByOrderByFechaInicioDesc();
}
