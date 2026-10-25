package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.SolicitudDescuento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SolicitudDescuentoRepository extends JpaRepository<SolicitudDescuento, Integer> {

    List<SolicitudDescuento> findByEstadoOrderByFechaSolicitudAsc(Byte estado);

    List<SolicitudDescuento> findAllByOrderByFechaSolicitudDesc(org.springframework.data.domain.Pageable limite);
}
