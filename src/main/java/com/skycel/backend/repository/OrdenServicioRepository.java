package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.OrdenServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrdenServicioRepository extends JpaRepository<OrdenServicio, Integer> {

    Optional<OrdenServicio> findByFolio(String folio);

    Optional<OrdenServicio> findByClaveOffline(String claveOffline);

    Optional<OrdenServicio> findByFolioLocal(String folioLocal);

    /** ¿Esta venta salió de una orden de servicio? (esa venta no se cancela por separado) */
    boolean existsByVenta_Idventa(Integer idventa);

    /** Órdenes de una tienda (de todas si codti es null), más recientes primero. Filtros opcionales por estado y técnico. */
    @Query("SELECT o FROM OrdenServicio o WHERE (:codti IS NULL OR o.tienda.codti = :codti) " +
           "AND (:estado IS NULL OR o.estado = :estado) " +
           "AND (:idTecnico IS NULL OR o.tecnico.idusuario = :idTecnico) " +
           "ORDER BY o.fechaIngreso DESC, o.idorden DESC")
    List<OrdenServicio> buscar(Integer codti, Byte estado, Integer idTecnico);

    /** Equipos listos que siguen en la tienda, los más antiguos primero. {@code codti} null = todas las sucursales. */
    @Query("SELECT o FROM OrdenServicio o WHERE o.estado = 3 AND (:codti IS NULL OR o.tienda.codti = :codti) " +
           "ORDER BY o.fechaLista ASC, o.idorden ASC")
    List<OrdenServicio> listasSinRecoger(Integer codti);

    /** Órdenes recibidas en un periodo (para el reporte de tiempos). {@code codti} null = todas las sucursales. */
    @Query("SELECT o FROM OrdenServicio o WHERE o.fechaIngreso >= :desde AND o.fechaIngreso < :hasta " +
           "AND (:codti IS NULL OR o.tienda.codti = :codti) ORDER BY o.fechaIngreso ASC")
    List<OrdenServicio> recibidasEntre(java.time.LocalDateTime desde, java.time.LocalDateTime hasta, Integer codti);

    /** Todas las órdenes abiertas de todas las sucursales (el taller atiende a todas las tiendas). */
    @Query("SELECT o FROM OrdenServicio o WHERE o.estado IN (1, 2, 3) ORDER BY o.fechaIngreso ASC, o.idorden ASC")
    List<OrdenServicio> abiertasDeTodas();

    /** Las abiertas que aún nadie ha tomado, o que ya tiene este técnico. */
    @Query("SELECT o FROM OrdenServicio o WHERE o.estado IN (1, 2, 3) AND (o.tecnico IS NULL OR o.tecnico.idusuario = :idTecnico) " +
           "ORDER BY o.fechaIngreso ASC, o.idorden ASC")
    List<OrdenServicio> abiertasPorTomarODelTecnico(Integer idTecnico);

    /** Órdenes asignadas a un técnico que aún están abiertas (recibidas, en reparación o listas). */
    @Query("SELECT o FROM OrdenServicio o WHERE o.tecnico.idusuario = :idTecnico AND o.estado IN (1, 2, 3) " +
           "ORDER BY o.fechaPromesa ASC, o.idorden ASC")
    List<OrdenServicio> abiertasDelTecnico(Integer idTecnico);
}
