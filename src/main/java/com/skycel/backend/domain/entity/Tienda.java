package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Audited
@Table(name = "tienda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codti")
    private Integer codti;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "ubicacion")
    private String ubicacion;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "es_almacen", columnDefinition = "TINYINT DEFAULT 0")
    private Boolean esAlmacen;

    /** El encargado actual de la tienda (usuario con rol de encargado). Se designa aquí, no en el empleado. */
    @Column(name = "idusuario_encargado")
    private Integer idEncargado;

    /** El taller donde trabajan los técnicos (solo una tienda lo es). */
    @Column(name = "es_taller", columnDefinition = "TINYINT DEFAULT 0")
    private Boolean esTaller;

    /** Para mostrar: el nombre del encargado actual (no se guarda). */
    @Transient
    private String nombreEncargado;

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    @Version
    @Column(name = "version")
    private Integer version;
}
