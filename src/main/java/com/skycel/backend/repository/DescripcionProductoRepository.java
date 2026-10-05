package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.DescripcionProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DescripcionProductoRepository extends JpaRepository<DescripcionProducto, Integer> {

    List<DescripcionProducto> findByCategoria_IdcatAndActivoTrueOrderByNombreAsc(Short idcat);

    /** Todas las de la categoría (activas o no): sirve para no repetir al crear. */
    List<DescripcionProducto> findByCategoria_Idcat(Short idcat);
}
