package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.InventarioAuditoriaUnidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioAuditoriaUnidadRepository extends JpaRepository<InventarioAuditoriaUnidad, Long> {

    List<InventarioAuditoriaUnidad> findByInventarioAuditoria_IdinventarioOrderByIdAsc(Integer idinventario);

    Optional<InventarioAuditoriaUnidad> findByInventarioAuditoria_IdinventarioAndImei(Integer idinventario, String imei);
}
