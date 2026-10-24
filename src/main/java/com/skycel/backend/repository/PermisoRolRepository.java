package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.PermisoRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermisoRolRepository extends JpaRepository<PermisoRol, Integer> {

    List<PermisoRol> findByPermiso(String permiso);

    void deleteByPermisoAndRolNot(String permiso, String rol);
}
