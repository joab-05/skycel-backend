package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Descripción guardada de un artículo ("A16 4/128gb", "iPhone 13"), colgada de la categoría más profunda como un nivel
 * más del árbol. Se elige de la lista o se crea una vez y se reutiliza (por ejemplo, la misma descripción con varios
 * colores) para no repetirla. El color va aparte, en el artículo.
 */
@Entity
@Table(name = "descripcion_producto",
        uniqueConstraints = @UniqueConstraint(name = "uk_descripcion_cat_nombre", columnNames = {"idcat", "nombre"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DescripcionProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "iddescripcion")
    private Integer iddescripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idcat", nullable = false)
    private Categoria categoria;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;
}
