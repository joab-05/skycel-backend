package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.EmpleadoDescanso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EmpleadoDescansoRepository extends JpaRepository<EmpleadoDescanso, Integer> {

    List<EmpleadoDescanso> findByEmpleado_IdempleadoAndFechaBetweenOrderByFechaDesc(Integer idempleado, LocalDate desde, LocalDate hasta);

    List<EmpleadoDescanso> findByEmpleado_Usuario_Tienda_CodtiAndFechaBetweenOrderByFechaDesc(Integer codti, LocalDate desde, LocalDate hasta);

    boolean existsByEmpleado_IdempleadoAndFecha(Integer idempleado, LocalDate fecha);
}
