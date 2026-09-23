package com.skycel.backend.domain.entity;

import com.skycel.backend.domain.enums.TipoProducto;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "producto_master")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idprodmaster")
    private Integer idprodmaster;

    @Column(name = "nombre_base", nullable = false, length = 255)
    private String nombreBase;

    // Solo para tipo CELULAR (Equipo): iPhone / 17 Pro Max 8/256gb.
    // nombreBase se calcula como "marca + modelo" cuando se registra así.
    @Column(name = "marca", length = 60)
    private String marca;

    @Column(name = "modelo", length = 120)
    private String modelo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idcat", nullable = false)
    private Categoria categoria;

    // Para ACCESORIO: la descripción específica del artículo (ej. "AirPods Pro 2 Gen").
    // Para SERVICIO: el tipo de reparación (ej. "Cambio de Pantalla"). No se usa para EQUIPO.
    @Column(name = "especificaciones", columnDefinition = "TEXT")
    private String especificaciones;

    // Solo para SERVICIO: el equipo al que aplica (ej. "Samsung A56 5G"), ya que el
    // mismo tipo de reparación puede tener precios distintos según el modelo.
    @Column(name = "nota_adicional", length = 500)
    private String notaAdicional;

    // Modelos o familias con los que sirve un ACCESORIO o SERVICIO (ej. "iPhone 13", "Samsung/Android",
    // "Universal"). Texto libre buscable; permite listar los accesorios de un equipo.
    @Column(name = "compatibilidad", length = 255)
    private String compatibilidad;

    // Solo SERVICIO: duración estimada del trabajo, en minutos.
    @Column(name = "tiempo_estimado_min")
    private Integer tiempoEstimadoMin;

    // Días de garantía que ofrece este artículo o servicio (null = sin garantía definida).
    @Column(name = "dias_garantia")
    private Integer diasGarantia;

    // Ordinal (TINYINT): ver TipoProducto — nunca reordenar sus valores.
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "tipo", nullable = false)
    private TipoProducto tipo = TipoProducto.ACCESORIO;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    @Version
    @Column(name = "version")
    private Integer version;
}
