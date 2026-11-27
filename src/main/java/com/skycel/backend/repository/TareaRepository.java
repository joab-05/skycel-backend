package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Tarea;
import com.skycel.backend.domain.enums.EstadoTarea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TareaRepository extends JpaRepository<Tarea, Integer> {

    @Query("select t from Tarea t join fetch t.tienda left join fetch t.hechaPor "
            + "where t.tienda.codti = :codti and t.fecha = :fecha")
    List<Tarea> delDia(@Param("codti") Integer codti, @Param("fecha") LocalDate fecha);

    /** Las rutinas de las que ya se generó la tarea de ese día en esa tienda. */
    @Query("select t.rutina.idrutina from Tarea t where t.tienda.codti = :codti and t.fecha = :fecha and t.rutina is not null")
    List<Integer> rutinasGeneradas(@Param("codti") Integer codti, @Param("fecha") LocalDate fecha);

    /** Con tienda, las de esa tienda; sin tienda, las de todas. */
    @Query("select t from Tarea t join fetch t.tienda left join fetch t.hechaPor "
            + "where t.fecha between :desde and :hasta and (:codti is null or t.tienda.codti = :codti)")
    List<Tarea> entre(@Param("codti") Integer codti, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Quita las tareas aún sin hacer de una rutina desde una fecha (al cambiarla o quitarla): las de hoy se vuelven a generar. */
    @Modifying
    @Query("delete from Tarea t where t.rutina.idrutina = :idrutina and t.fecha >= :desde and t.estado = :estado")
    int borrarDesde(@Param("idrutina") Integer idrutina, @Param("desde") LocalDate desde, @Param("estado") EstadoTarea estado);
}
