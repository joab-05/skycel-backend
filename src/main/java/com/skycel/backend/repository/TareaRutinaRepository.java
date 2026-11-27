package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.TareaRutina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TareaRutinaRepository extends JpaRepository<TareaRutina, Integer> {

    /** Las actividades activas que le tocan a una tienda: las suyas y las de todas las tiendas. */
    @Query("select r from TareaRutina r where r.activa = true and (r.tienda is null or r.tienda.codti = :codti)")
    List<TareaRutina> activasDe(@Param("codti") Integer codti);

    /** Para la pantalla de rutinas: sin tienda, todas; con tienda, las suyas y las de todas las tiendas (activas o no). */
    @Query("select r from TareaRutina r left join fetch r.tienda "
            + "where (:codti is null or r.tienda is null or r.tienda.codti = :codti)")
    List<TareaRutina> listar(@Param("codti") Integer codti);
}
