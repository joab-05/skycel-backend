package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.OrdenServicioFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrdenServicioFotoRepository extends JpaRepository<OrdenServicioFoto, Integer> {

    /** Los datos de las fotos sin la imagen (para no cargar los archivos al listar). */
    interface Resumen {
        Integer getIdfoto();
        String getDescripcion();
        String getTipo();
        LocalDateTime getFecha();
    }

    List<Resumen> findByOrden_IdordenOrderByIdfoto(Integer idorden);

    long countByOrden_Idorden(Integer idorden);
}
