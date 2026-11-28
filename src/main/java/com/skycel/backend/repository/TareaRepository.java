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

    @Query("select t from Tarea t join fetch t.tienda left join fetch t.hechaPor left join fetch t.asignadoA left join fetch t.revisadaPor "
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

    /**
     * Las tareas puntuales que le tocan a alguien (o son de la tienda) y siguen a la vista: las que aún no se hacen y vencen de aquí
     * a unos días (o ya vencieron), las de hoy y las que esperan revisión.
     */
    @Query("select t from Tarea t join fetch t.tienda left join fetch t.hechaPor left join fetch t.asignadoA a left join fetch t.revisadaPor "
            + "where t.rutina is null and (t.tienda.codti = :codti or a.idusuario = :idusuario) "
            + "and (t.fecha = :hoy or t.estado = com.skycel.backend.domain.enums.EstadoTarea.POR_REVISAR "
            + "or (t.estado = com.skycel.backend.domain.enums.EstadoTarea.PENDIENTE and t.fecha <= :hasta))")
    List<Tarea> puntualesVisibles(@Param("codti") Integer codti, @Param("idusuario") Integer idusuario,
                                  @Param("hoy") LocalDate hoy, @Param("hasta") LocalDate hasta);

    /** Lo que sigue abierto de días anteriores en una tienda: lo que espera revisión y las tareas puntuales que se vencieron sin hacer. */
    @Query("select t from Tarea t join fetch t.tienda left join fetch t.hechaPor left join fetch t.asignadoA left join fetch t.revisadaPor "
            + "where t.tienda.codti = :codti and t.fecha < :hoy and (t.estado = com.skycel.backend.domain.enums.EstadoTarea.POR_REVISAR "
            + "or (t.rutina is null and t.estado = com.skycel.backend.domain.enums.EstadoTarea.PENDIENTE))")
    List<Tarea> abiertasAnteriores(@Param("codti") Integer codti, @Param("hoy") LocalDate hoy);

    @Query("select t from Tarea t join fetch t.tienda left join fetch t.hechaPor left join fetch t.asignadoA left join fetch t.revisadaPor "
            + "where t.tienda.codti = :codti and t.estado = com.skycel.backend.domain.enums.EstadoTarea.POR_REVISAR order by t.hechaEl")
    List<Tarea> porRevisar(@Param("codti") Integer codti);
}
