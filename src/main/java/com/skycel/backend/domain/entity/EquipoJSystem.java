package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Una computadora con JSystem instalado, tal como se reporta a sí misma cada pocos minutos: qué versión tiene, si ya bajó
 * una más nueva que espera reinicio y si la actualización falló. Sirve para saber que todas están al día.
 */
@Entity
@Table(name = "equipo_jsystem", uniqueConstraints = @UniqueConstraint(name = "uk_equipo_jsystem_host", columnNames = "host"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EquipoJSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** El nombre de la computadora en Windows: identifica al equipo aunque nadie le haya puesto nombre en Configuración. */
    @Column(name = "host", nullable = false, length = 80)
    private String host;

    /** El «Nombre del equipo» de Configuración (por ejemplo «Caja Zócalo»); puede venir vacío. */
    @Column(name = "nombre_equipo", length = 80)
    private String nombreEquipo;

    @Column(name = "version", length = 30)
    private String version;

    /** La versión ya descargada que espera a que se cierre el programa para instalarse. */
    @Column(name = "version_pendiente", length = 30)
    private String versionPendiente;

    /** Lo último que dijo el actualizador («Este equipo ya tiene la versión más nueva», «No se pudo actualizar: …»). */
    @Column(name = "estado_actualizacion", length = 300)
    private String estadoActualizacion;

    /** ¿La última revisión o descarga terminó en error? */
    @Column(name = "con_error", nullable = false)
    @Builder.Default
    private Boolean conError = false;

    @Column(name = "usuario", length = 100)
    private String usuario;

    @Column(name = "codti")
    private Integer codti;

    @Column(name = "nombre_tienda", length = 100)
    private String nombreTienda;

    @Column(name = "ultima_conexion", nullable = false)
    private LocalDateTime ultimaConexion;
}
