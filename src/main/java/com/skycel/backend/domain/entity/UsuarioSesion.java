package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_sesion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idsesion")
    private Long idsesion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario", nullable = false)
    private Usuario usuario;

    @Column(name = "token_uuid", nullable = false, length = 36, unique = true)
    private String tokenUuid;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "fecha_ingreso", updatable = false)
    private LocalDateTime fechaIngreso;

    @Column(name = "fecha_salida")
    private LocalDateTime fechaSalida;

    @Column(name = "motivo_cierre", length = 50)
    private String motivoCierre;
}
