package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.InventarioAuditoriaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioAuditoriaDetalleRepository extends JpaRepository<InventarioAuditoriaDetalle, Integer> {

    List<InventarioAuditoriaDetalle> findByInventarioAuditoria_IdinventarioOrderByIddetalleInvAsc(Integer idinventario);

    /** Ya se escaneó este artículo en esta auditoría — para acumular el conteo en vez de duplicar la fila. */
    Optional<InventarioAuditoriaDetalle> findByInventarioAuditoria_IdinventarioAndCodpro(Integer idinventario, String codpro);
}
