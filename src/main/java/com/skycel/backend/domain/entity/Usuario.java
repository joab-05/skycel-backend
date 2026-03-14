package com.skycel.backend.domain.entity;

import com.skycel.backend.domain.enums.Rol;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Audited
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idusuario")
    private Integer idusuario;

    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti", nullable = false)
    private Tienda tienda;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "rol", nullable = false, columnDefinition = "TINYINT")
    private Rol rol;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    @CreationTimestamp
    @Column(name = "fecha_alta", updatable = false)
    private LocalDateTime fechaAlta;

    @Version
    @Column(name = "version")
    private Integer version;
}
