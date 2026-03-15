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
    private Integer idsesion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idusuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codti_terminal", nullable = false)
    private Tienda tiendaTerminal;

    @Column(name = "token_uuid", nullable = false, length = 64, unique = true)
    private String tokenUuid;

    @Column(name = "ip_publica", nullable = false, length = 45)
    private String ipPublica;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "dispositivo_info", length = 100)
    private String dispositivoInfo;

    @CreationTimestamp
    @Column(name = "fecha_ingreso", updatable = false)
    private LocalDateTime fechaIngreso;

    @Column(name = "fecha_salida")
    private LocalDateTime fechaSalida;
}
