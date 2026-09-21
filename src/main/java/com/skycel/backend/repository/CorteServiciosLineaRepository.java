package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.CorteServiciosLinea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CorteServiciosLineaRepository extends JpaRepository<CorteServiciosLinea, Integer> {

    List<CorteServiciosLinea> findByCorte_IdcorteOrderByIdlinea(Integer idcorte);

    void deleteByCorte_Idcorte(Integer idcorte);
}
