package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Integer> {

    List<Caja> findByTienda_Codti(Integer codti);
}