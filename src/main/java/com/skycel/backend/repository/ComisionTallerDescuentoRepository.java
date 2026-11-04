package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ComisionTallerDescuento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComisionTallerDescuentoRepository extends JpaRepository<ComisionTallerDescuento, Integer> {

    List<ComisionTallerDescuento> findByAnioAndMesOrderByIddescuentoAsc(Integer anio, Integer mes);
}
