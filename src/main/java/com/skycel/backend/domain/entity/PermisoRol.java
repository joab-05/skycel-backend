package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Un rol que tiene un permiso (ver {@link com.skycel.backend.domain.enums.Permiso}). Una fila con rol {@code "*"} es la
 * marca de que ese permiso ya se sembró con sus valores iniciales: así, si root le quita todos los roles a un permiso,
 * al reiniciar no se le vuelven a poner.
 */
@Entity
@Table(name = "permiso_rol", uniqueConstraints = @UniqueConstraint(columnNames = {"permiso", "rol"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermisoRol {

    public static final String MARCA_SEMBRADO = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "permiso", nullable = false, length = 40)
    private String permiso;

    @Column(name = "rol", nullable = false, length = 30)
    private String rol;
}
