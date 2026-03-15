package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Short> {
    List<Proveedor> findByActivoTrue();
}
