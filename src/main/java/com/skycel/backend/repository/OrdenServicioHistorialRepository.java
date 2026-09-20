package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.OrdenServicioHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdenServicioHistorialRepository extends JpaRepository<OrdenServicioHistorial, Integer> {

    List<OrdenServicioHistorial> findByOrden_IdordenOrderByIdhistorial(Integer idorden);
}
