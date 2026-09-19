package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Seccion;
import com.skycel.backend.domain.entity.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TiendaRepository extends JpaRepository<Tienda, Integer> {

}
