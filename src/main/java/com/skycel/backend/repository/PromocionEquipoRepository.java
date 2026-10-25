package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.PromocionEquipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromocionEquipoRepository extends JpaRepository<PromocionEquipo, Integer> {
}
