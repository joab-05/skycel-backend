package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Un renglón por categoría que se use para autogenerar códigos de accesorio
 * (formato {codigoCategoria}-{folio}). idcat es a la vez la PK y la FK a
 * Categoria — no hay generación automática de id, se usa el mismo que la
 * categoría. El incremento atómico vive en CategoriaFolioRepository, con el
 * idiom estándar de MySQL "INSERT ... ON DUPLICATE KEY UPDATE ... LAST_INSERT_ID(...)".
 */
@Entity
@Table(name = "categoria_folio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaFolio {

    @Id
    @Column(name = "idcat")
    private Short idcat;

    @Column(name = "ultimo_folio", nullable = false)
    private Long ultimoFolio;
}
