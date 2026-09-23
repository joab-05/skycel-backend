package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    Optional<Usuario> findByUsername(String username);

    List<Usuario> findByTienda_CodtiAndActivoTrue(Integer codti);

    List<Usuario> findByRolAndActivoTrue(Rol rol);
}