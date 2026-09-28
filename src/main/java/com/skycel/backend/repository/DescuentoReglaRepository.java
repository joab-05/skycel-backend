package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.DescuentoRegla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DescuentoReglaRepository extends JpaRepository<DescuentoRegla, Integer> {

    List<DescuentoRegla> findByActivoTrue();

    List<DescuentoRegla> findAllByOrderByFechaRegistroDesc();
}
