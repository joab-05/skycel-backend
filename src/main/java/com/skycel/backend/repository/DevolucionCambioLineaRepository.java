package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.DevolucionCambioLinea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DevolucionCambioLineaRepository extends JpaRepository<DevolucionCambioLinea, Integer> {

    List<DevolucionCambioLinea> findByDevolucion_IddevolucionOrderByIdlinea(Integer iddevolucion);
}
