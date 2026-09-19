package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Integer> {

    List<Venta> findByTienda_CodtiAndFechaVentaBetweenOrderByFechaVentaDesc(
            Integer codti, LocalDateTime desde, LocalDateTime hasta);

    List<Venta> findByCaja_IdCajaAndFechaVentaGreaterThanEqualOrderByFechaVentaDesc(
            Integer idCaja, LocalDateTime desde);

    List<Venta> findByCliente_IdclienteOrderByFechaVentaDesc(Integer idcliente);
}