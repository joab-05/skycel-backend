package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ConfiguracionNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionNegocioRepository extends JpaRepository<ConfiguracionNegocio, Integer> {
}
