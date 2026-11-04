package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ComisionTallerTecnico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComisionTallerTecnicoRepository extends JpaRepository<ComisionTallerTecnico, Integer> {
}
