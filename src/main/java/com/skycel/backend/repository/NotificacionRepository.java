package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    List<Notificacion> findByUsuarioDestino_IdusuarioAndLeidaFalseOrderByFechaCreacionDesc(Integer idusuario);

    List<Notificacion> findTop100ByUsuarioDestino_IdusuarioOrderByFechaCreacionDesc(Integer idusuario);

    long countByUsuarioDestino_IdusuarioAndLeidaFalse(Integer idusuario);
}
