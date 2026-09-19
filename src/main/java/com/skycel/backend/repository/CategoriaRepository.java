package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Categoria;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Short> {
    List<Categoria> findByActivoTrue();
    @EntityGraph(attributePaths = {"subcategorias"})
    List<Categoria> findByCategoriaSuperiorIsNullAndActivoTrue();//Permite obtener todas las categorias y subcategorias

    // ========== VALIDACIONES PARA POST (nuevas categorías) ==========

    // Para categorías PADRE (idcatsup IS NULL)
    boolean existsByNombreIgnoreCaseAndCategoriaSuperiorIsNull(String nombre);

    // Para subcategorías (mismo padre)
    boolean existsByNombreIgnoreCaseAndCategoriaSuperiorIdcat(String nombre, Short idCategoriaSuperior);

    // ========== VALIDACIONES PARA PUT (actualizaciones) ==========

    // Para categorías PADRE excluyendo el ID actual
    boolean existsByNombreIgnoreCaseAndCategoriaSuperiorIsNullAndIdcatNot(
            String nombre,
            Short id
    );

    // Para subcategorías excluyendo el ID actual
    boolean existsByNombreIgnoreCaseAndCategoriaSuperiorIdcatAndIdcatNot(
            String nombre,
            Short idCategoriaSuperior,
            Short id
    );

    // Para obtener todos los IDs descendientes (prevenir ciclos)
    @Query("SELECT c.idcat FROM Categoria c WHERE c.categoriaSuperior.idcat = :parentId")
    List<Short> findIdsByCategoriaSuperiorId(@Param("parentId") Short parentId);
}
