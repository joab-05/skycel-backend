package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.EmpleadoDescansoAjuste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmpleadoDescansoAjusteRepository extends JpaRepository<EmpleadoDescansoAjuste, Integer> {

    List<EmpleadoDescansoAjuste> findByEmpleado_IdempleadoOrderByFechaDesc(Integer idempleado);

    List<EmpleadoDescansoAjuste> findByEmpleado_Idempleado(Integer idempleado);
}
