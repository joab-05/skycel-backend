package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.TraspasoFaltanteMov;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TraspasoFaltanteMovRepository extends JpaRepository<TraspasoFaltanteMov, Integer> {

    List<TraspasoFaltanteMov> findByDetalle_IddetalleOrderByIdmov(Integer iddetalle);
}
