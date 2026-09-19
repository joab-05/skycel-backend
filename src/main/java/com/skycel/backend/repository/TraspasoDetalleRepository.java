package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.TraspasoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TraspasoDetalleRepository extends JpaRepository<TraspasoDetalle, Integer> {

    List<TraspasoDetalle> findByTraspaso_IdtraspasoOrderByIddetalle(Integer idtraspaso);
}
