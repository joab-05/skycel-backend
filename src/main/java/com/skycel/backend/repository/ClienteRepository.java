package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    List<Cliente> findByActivoTrue();

    boolean existsByTelefono(String telefono);

    java.util.Optional<Cliente> findByTelefono(String telefono);

    /** Cuenta ventas completadas de un cliente */
    @Query("SELECT COUNT(v) FROM Venta v WHERE v.cliente.idcliente = :id AND v.estado = 1")
    long contarVentasPorCliente(@Param("id") Integer id);

    /** Suma total gastado por un cliente */
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM Venta v WHERE v.cliente.idcliente = :id AND v.estado = 1")
    BigDecimal totalGastadoPorCliente(@Param("id") Integer id);
}