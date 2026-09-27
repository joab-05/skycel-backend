package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.PagoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagoCompraRepository extends JpaRepository<PagoCompra, Integer> {

    List<PagoCompra> findByCompra_IdcompraOrderByFechaPagoDesc(Integer idcompra);
}
