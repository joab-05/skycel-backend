package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.EmpleadoDescansoSaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmpleadoDescansoSaldoRepository extends JpaRepository<EmpleadoDescansoSaldo, Integer> {
}
