package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Magnitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MagnitudRepository extends JpaRepository<Magnitud, Short> {
    List<Magnitud> findByActivoTrue();

    // Para validar duplicados en POST (nombre debe ser único)
    boolean existsByNombreIgnoreCase(String nombre);

    // Para validar duplicados en PUT (excluyendo el ID actual)
    boolean existsByNombreIgnoreCaseAndIdmagnitudNot(String nombre, Short id);

    // Buscar por nombre (útil para mensajes de error)
    Optional<Magnitud> findByNombreIgnoreCase(String nombre);
}
