package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cliente", indexes = @Index(name = "idx_cliente_telefono", columnList = "telefono"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idcliente")
    private Integer idcliente;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "telefono", length = 15)
    private String telefono;

    @Column(name = "correo", length = 100)
    private String correo;

    @Column(name = "direccion", length = 255)
    private String direccion;

    @Column(name = "tipo_cliente", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private Byte tipoCliente;          // 1=Regular 2=Frecuente 3=VIP

    @Column(name = "margen_factor", precision = 5, scale = 2)
    private BigDecimal margenFactor;

    @Column(name = "puntos", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer puntos = 0;        // puntos de lealtad acumulados

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "activo", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean activo;
}
