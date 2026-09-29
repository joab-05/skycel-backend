package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.EmpleadoPrestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoPrestamoRepository extends JpaRepository<EmpleadoPrestamo, Integer> {

    Optional<EmpleadoPrestamo> findByEmpleado_IdempleadoAndEstado(Integer idempleado, Byte estado);

    List<EmpleadoPrestamo> findByEmpleado_IdempleadoOrderByFechaDesc(Integer idempleado);
}
