package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.CatMotivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatMotivoRepository extends JpaRepository<CatMotivo, Integer> {

    Optional<CatMotivo> findByNombre(String nombre);

    List<CatMotivo> findByActivoTrue();

    List<CatMotivo> findByTipoMovAndActivoTrue(Byte tipoMov);
}