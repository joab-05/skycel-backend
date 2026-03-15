package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Magnitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MagnitudRepository extends JpaRepository<Magnitud, Short> {
    List<Magnitud> findByActivoTrue();
}
