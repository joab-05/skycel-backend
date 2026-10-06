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

    long countByCategoria_IdcatAndActivoTrue(Short idcat);

    long countByDescripcion_IddescripcionAndActivoTrue(Integer iddescripcion);

    long countByColor_IdcolorAndActivoTrue(Short idcolor);

    /** Los artículos que usan esa descripción del catálogo. */
    List<ProductoMaster> findByDescripcion_Iddescripcion(Integer iddescripcion);

    /** Los artículos (activos o no) que cuelgan de cualquiera de esas categorías. */
    List<ProductoMaster> findByCategoria_IdcatIn(java.util.Collection<Short> idcats);

    @Query("SELECT p FROM ProductoMaster p WHERE p.activo = true AND " +
           "(LOWER(p.nombreBase) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.notaAdicional) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.compatibilidad) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ProductoMaster> searchByKeyword(String keyword);
}
