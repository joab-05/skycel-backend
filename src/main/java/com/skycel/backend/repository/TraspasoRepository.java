package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Traspaso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TraspasoRepository extends JpaRepository<Traspaso, Integer> {

    /** Traspasos que entran o salen de una tienda, más recientes primero. Filtros opcionales por tipo y estado. */
    @Query("SELECT t FROM Traspaso t WHERE (t.tiendaOrigen.codti = :codti OR t.tiendaDestino.codti = :codti) " +
           "AND (:tipo IS NULL OR t.tipo = :tipo) AND (:estado IS NULL OR t.estado = :estado) " +
           "AND (t.activo = true OR :incluirAnulados = true) ORDER BY t.fechaCreacion DESC, t.idtraspaso DESC")
    List<Traspaso> buscar(Integer codti, Byte tipo, Byte estado, boolean incluirAnulados);

    /** Lo que la tienda tiene por atender: envíos por recibir y solicitudes sin resolver. */
    @Query("SELECT t FROM Traspaso t WHERE t.tiendaDestino.codti = :codti AND t.activo = true AND " +
           "((t.tipo = 1 AND t.estado = 1) OR (t.tipo = 2 AND t.estado IN (1, 3))) " +
           "ORDER BY t.fechaCreacion ASC, t.idtraspaso ASC")
    List<Traspaso> pendientesDeLaTienda(Integer codti);
}
