package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ProductoMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoMasterRepository extends JpaRepository<ProductoMaster, Integer> {
    
    List<ProductoMaster> findByActivoTrue();

    Optional<ProductoMaster> findByNombreBaseIgnoreCaseAndActivoTrue(String nombreBase);
    
    List<ProductoMaster> findByCategoria_IdcatAndActivoTrue(Short idcat);

    @Query("SELECT p FROM ProductoMaster p WHERE p.activo = true AND " +
           "(LOWER(p.nombreBase) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.notaAdicional) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ProductoMaster> searchByKeyword(String keyword);
}
