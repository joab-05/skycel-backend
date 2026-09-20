package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.GarantiaHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GarantiaHistorialRepository extends JpaRepository<GarantiaHistorial, Integer> {

    List<GarantiaHistorial> findByGarantia_IdgarantiaOrderByIdhistorial(Integer idgarantia);
}
