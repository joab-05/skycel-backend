package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ServicioTipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicioTipoRepository extends JpaRepository<ServicioTipo, Integer> {

    List<ServicioTipo> findByActivoTrueOrderByOrdenAscIdtipoAsc();

    List<ServicioTipo> findAllByOrderByOrdenAscIdtipoAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
