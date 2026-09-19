package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.CategoriaFolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaFolioRepository extends JpaRepository<CategoriaFolio, Short> {

    /**
     * Inserta el contador en 1 si no existe, o lo incrementa atómicamente si ya existe.
     * La fila queda bloqueada por esta transacción, así que obtenerUltimoFolioGenerado()
     * lee el valor recién asignado sin riesgo de que otra transacción lo modifique.
     * Debe llamarse dentro de un método @Transactional junto con el SELECT de abajo.
     * (No se usa LAST_INSERT_ID(expr): en el primer INSERT de una categoría no se asigna
     * y devuelve el último AUTO_INCREMENT de la conexión, p. ej. el id de un producto maestro.)
     */
    @Modifying
    @Query(value = "INSERT INTO categoria_folio (idcat, ultimo_folio) VALUES (:idcat, 1) " +
            "ON DUPLICATE KEY UPDATE ultimo_folio = ultimo_folio + 1",
            nativeQuery = true)
    void incrementar(@Param("idcat") Short idcat);

    @Query(value = "SELECT ultimo_folio FROM categoria_folio WHERE idcat = :idcat", nativeQuery = true)
    Long obtenerUltimoFolioGenerado(@Param("idcat") Short idcat);
}
