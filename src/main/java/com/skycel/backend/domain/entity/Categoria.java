package com.skycel.backend.domain.entity;

import com.skycel.backend.domain.enums.TipoProducto;
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

    /** A qué tipo de producto pertenece esta categoría (ordinal/TINYINT): un Accesorio no debe ver categorías de Servicio o viceversa. */
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "tipo", nullable = false)
    private TipoProducto tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcatsup")
    private Categoria categoriaSuperior;

    @Builder.Default
    @OneToMany(mappedBy = "categoriaSuperior", cascade = CascadeType.ALL)
    @SQLRestriction("activo = '1'")
    private List<Categoria> subcategorias = new ArrayList<>();

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    /**
     * Si el nombre de esta categoría forma parte del nombre completo de los artículos que cuelgan de ella
     * (ej. "Celular" sí: "Celular Samsung A16"; "Reparación" no: "Cambio de Pantalla iPhone 13").
     * null se trata como true; la categoría principal normalmente va en false.
     */
    @Column(name = "incluir_en_nombre", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean incluirEnNombre;

    /** Máximo de niveles del árbol: principal, subcategoría 1 y subcategoría 2 (opcional). */
    public static final int NIVELES_MAX = 3;

    public boolean incluyeEnNombre() {
        return incluirEnNombre == null || incluirEnNombre;
    }

    /** Camino desde la categoría principal hasta esta categoría (ambas incluidas). */
    public List<Categoria> ruta() {
        java.util.LinkedList<Categoria> ruta = new java.util.LinkedList<>();
        for (Categoria c = this; c != null && ruta.size() <= NIVELES_MAX + 5; c = c.getCategoriaSuperior()) {
            ruta.addFirst(c);
        }
        return ruta;
    }

    /** 1 = principal, 2 = subcategoría 1, 3 = subcategoría 2. */
    public int nivel() {
        return ruta().size();
    }

    public Categoria raiz() {
        return ruta().get(0);
    }

    /** La subcategoría 1 de la que cuelga (ella misma si lo es); null si es una categoría principal. Define el prefijo del código. */
    public Categoria subcategoria1() {
        List<Categoria> r = ruta();
        return r.size() >= 2 ? r.get(1) : null;
    }
}
