package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ComisionPeriodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComisionPeriodoRepository extends JpaRepository<ComisionPeriodo, Integer> {

    Optional<ComisionPeriodo> findByTipoAndEmpleado_IdempleadoAndAnioAndMes(Byte tipo, Integer idempleado, Integer anio, Integer mes);

    List<ComisionPeriodo> findByTipoAndAnioAndMes(Byte tipo, Integer anio, Integer mes);
}
