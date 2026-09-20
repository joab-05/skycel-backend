package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.Garantia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GarantiaRepository extends JpaRepository<Garantia, Integer> {

    Optional<Garantia> findByFolioSeguimiento(String folioSeguimiento);

    /** Reclamos de una venta sobre un producto (para no abrir dos a la vez sobre el mismo equipo). */
    List<Garantia> findByVenta_IdventaAndProducto_Idproducto(Integer idventa, Integer idproducto);

    /**
     * Reclamos, más recientes primero. Filtros opcionales por sucursal que recibió y por estado; solo abiertos (no
     * entregados) y solo los que ya pasaron su fecha límite.
     */
    @Query("SELECT g FROM Garantia g WHERE (:codti IS NULL OR g.tienda.codti = :codti) " +
           "AND (:estado IS NULL OR g.estadoActual = :estado) " +
           "AND (:soloAbiertas = false OR g.estadoActual <> 11) " +
           "AND (:vencidas = false OR (g.estadoActual <> 11 AND g.fechaLimiteSolucion < :hoy)) " +
           "ORDER BY g.fechaIngreso DESC, g.idgarantia DESC")
    List<Garantia> buscar(Integer codti, Byte estado, boolean soloAbiertas, boolean vencidas, LocalDate hoy);
}
