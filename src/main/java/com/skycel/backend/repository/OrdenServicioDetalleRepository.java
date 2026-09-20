package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.OrdenServicioDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdenServicioDetalleRepository extends JpaRepository<OrdenServicioDetalle, Integer> {

    List<OrdenServicioDetalle> findByOrden_IdordenOrderByIddetalle(Integer idorden);
}
