package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Color;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColorRepository extends JpaRepository<Color, Short> {
    List<Color> findByActivoTrue();
}
