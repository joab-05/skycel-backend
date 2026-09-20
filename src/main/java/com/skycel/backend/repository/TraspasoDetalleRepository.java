package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.TraspasoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TraspasoDetalleRepository extends JpaRepository<TraspasoDetalle, Integer> {

    List<TraspasoDetalle> findByTraspaso_IdtraspasoOrderByIddetalle(Integer idtraspaso);

    /**
     * Renglones de envíos recibidos con faltantes que aún no se resuelven del todo. Filtro opcional por tienda (origen o
     * destino).
     */
    @org.springframework.data.jpa.repository.Query("SELECT d FROM TraspasoDetalle d WHERE d.traspaso.tipo = 1 AND d.traspaso.estado = 2 " +
            "AND d.traspaso.activo = true AND d.traspaso.conFaltantes = true " +
            "AND (d.cantidad - COALESCE(d.cantidadRecibida, 0) - d.cantidadBaja - d.cantidadReintegrada) > 0 " +
            "AND (:codti IS NULL OR d.traspaso.tiendaOrigen.codti = :codti OR d.traspaso.tiendaDestino.codti = :codti) " +
            "ORDER BY d.traspaso.idtraspaso, d.iddetalle")
    List<TraspasoDetalle> faltantesPendientes(@org.springframework.data.repository.query.Param("codti") Integer codti);
}
