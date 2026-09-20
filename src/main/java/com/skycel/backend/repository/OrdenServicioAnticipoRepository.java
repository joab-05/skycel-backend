package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.OrdenServicioAnticipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdenServicioAnticipoRepository extends JpaRepository<OrdenServicioAnticipo, Integer> {

    List<OrdenServicioAnticipo> findByOrden_IdordenOrderByIdanticipo(Integer idorden);
}
