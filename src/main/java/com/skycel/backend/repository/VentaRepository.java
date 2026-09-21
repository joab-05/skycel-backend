package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Integer> {

    /** La venta que ya se registró con esa clave (idempotencia de las ventas hechas sin conexión). */
    java.util.Optional<Venta> findByClaveOffline(String claveOffline);

    java.util.Optional<Venta> findByFolioLocal(String folioLocal);

    List<Venta> findByTienda_CodtiAndFechaVentaBetweenOrderByFechaVentaDesc(
            Integer codti, LocalDateTime desde, LocalDateTime hasta);

    List<Venta> findByCaja_IdCajaAndFechaVentaGreaterThanEqualOrderByFechaVentaDesc(
            Integer idCaja, LocalDateTime desde);

    List<Venta> findByCliente_IdclienteOrderByFechaVentaDesc(Integer idcliente);
}