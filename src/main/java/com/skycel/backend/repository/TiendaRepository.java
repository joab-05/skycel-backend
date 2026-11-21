package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Seccion;
import com.skycel.backend.domain.entity.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TiendaRepository extends JpaRepository<Tienda, Integer> {

    /** La tienda de la que este usuario es el encargado actual. */
    Optional<Tienda> findFirstByIdEncargado(Integer idusuario);

    /** La tienda marcada como taller de los técnicos. */
    List<Tienda> findByEsTallerTrue();

}
