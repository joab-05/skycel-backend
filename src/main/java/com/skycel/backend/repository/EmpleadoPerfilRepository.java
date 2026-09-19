package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.EmpleadoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmpleadoPerfilRepository extends JpaRepository<EmpleadoPerfil, Integer> {

    Optional<EmpleadoPerfil> findByUsuarioIdusuario(Integer idusuario);
}