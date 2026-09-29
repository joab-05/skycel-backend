package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.InventarioAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioAuditoriaRepository extends JpaRepository<InventarioAuditoria, Integer> {

    /** La auditoría en curso (abierta o catalogando) de una sucursal, si hay una — solo puede haber una a la vez. */
    Optional<InventarioAuditoria> findFirstByTienda_CodtiAndEstadoInOrderByFechaInicioDesc(Integer codti, Collection<Byte> estados);

    List<InventarioAuditoria> findByTienda_CodtiOrderByFechaInicioDesc(Integer codti);
}
