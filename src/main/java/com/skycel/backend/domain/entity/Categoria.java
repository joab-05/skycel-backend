package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idcat")
    private Short idcat;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    // Código corto (ej. "AUD", "MIC", "CAR") usado como prefijo del código autogenerado
    // de accesorios en esa subcategoría. Solo obligatorio para subcategorías de Accesorios
    // que se usen para registrar productos — ver ProductoService.generarCodigoAccesorio.
    @Column(name = "codigo", length = 10)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcatsup")
    private Categoria categoriaSuperior;

    @Builder.Default
    @OneToMany(mappedBy = "categoriaSuperior", cascade = CascadeType.ALL)
    @SQLRestriction("activo = '1'")
    private List<Categoria> subcategorias = new ArrayList<>();

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;
}
