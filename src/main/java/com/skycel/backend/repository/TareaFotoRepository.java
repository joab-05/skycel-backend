package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.TareaFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TareaFotoRepository extends JpaRepository<TareaFoto, Integer> {

    long countByTarea_Idtarea(Integer idtarea);

    /** Qué fotos tiene una tarea, sin traer las imágenes: id, tipo, fecha, id y nombre de quien la subió. */
    @Query("select f.idfoto, f.tipo, f.fecha, u.idusuario, u.nombreCompleto from TareaFoto f left join f.usuario u "
            + "where f.tarea.idtarea = :idtarea order by f.idfoto")
    List<Object[]> resumenes(@Param("idtarea") Integer idtarea);

    @Modifying
    @Query("delete from TareaFoto f where f.tarea.idtarea = :idtarea")
    int borrarDeTarea(@Param("idtarea") Integer idtarea);

    /** Las fotos de las tareas aún sin hacer de una rutina desde una fecha (antes de regenerarlas o quitarlas). */
    @Modifying
    @Query("delete from TareaFoto f where f.tarea.idtarea in (select t.idtarea from Tarea t where t.rutina.idrutina = :idrutina "
            + "and t.fecha >= :desde and t.estado = com.skycel.backend.domain.enums.EstadoTarea.PENDIENTE)")
    int borrarPendientesDeRutina(@Param("idrutina") Integer idrutina, @Param("desde") LocalDate desde);
}
