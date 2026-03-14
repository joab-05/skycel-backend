package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.UsuarioSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioSesionRepository extends JpaRepository<UsuarioSesion, Long> {

    Optional<UsuarioSesion> findByTokenUuidAndFechaSalidaIsNull(String tokenUuid);

    @Modifying
    @Query("UPDATE UsuarioSesion s SET s.fechaSalida = CURRENT_TIMESTAMP, s.motivoCierre = :motivo WHERE s.tokenUuid = :uuid")
    int invalidateSession(@Param("uuid") String uuid, @Param("motivo") String motivo);
}
