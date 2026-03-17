package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    Optional<Producto> findByCodpro(String codpro);

    Optional<Producto> findByCodproAndTienda_Codti(String codpro, Integer codti);

    List<Producto> findByTienda_CodtiAndActivoTrue(Integer codti);
    
    List<Producto> findByProductoMaster_IdprodmasterAndTienda_CodtiAndActivoTrue(Integer idprodmaster, Integer codti);

    @Query("SELECT p FROM Producto p WHERE p.tienda.codti = :codti AND p.stock > 0 AND p.activo = true")
    List<Producto> findAvailableStockByTienda(Integer codti);
}
