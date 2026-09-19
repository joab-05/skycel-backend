package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.VentaPagoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VentaPagoDetalleRepository extends JpaRepository<VentaPagoDetalle, Integer> {

    List<VentaPagoDetalle> findByVenta_IdventaOrderByIdpagoDetalle(Integer idventa);
}
