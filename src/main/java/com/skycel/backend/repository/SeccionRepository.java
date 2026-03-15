package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Seccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeccionRepository extends JpaRepository<Seccion, Short> {
    List<Seccion> findByActivoTrue();
}
