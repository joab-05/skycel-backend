package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface GastoRepository extends JpaRepository<Gasto, Integer> {

    /** Los gastos de una sola vez, de un periodo. */
    List<Gasto> findByActivoTrueAndRecurrenteFalseAndFechaBetweenOrderByFechaAscIdgastoAsc(LocalDate desde, LocalDate hasta);

    /** Los recurrentes que ya empezaron (los que ya terminaron se descartan al calcular). */
    List<Gasto> findByActivoTrueAndRecurrenteTrueAndFechaLessThanEqualOrderByFechaAscIdgastoAsc(LocalDate hasta);
}
