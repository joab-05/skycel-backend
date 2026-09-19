package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ProductoImei;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoImeiRepository extends JpaRepository<ProductoImei, Long> {

    Optional<ProductoImei> findByImei(String imei);

    List<ProductoImei> findByProducto_IdproductoAndEstado(Integer idproducto, String estado);

    boolean existsByImei(String imei);
}
