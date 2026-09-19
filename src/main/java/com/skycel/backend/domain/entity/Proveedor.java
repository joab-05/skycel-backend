package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "proveedor")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idproveedor")
    private Short idproveedor;

    @Column(name = "nombre_fiscal", nullable = false, length = 150)
    private String nombreFiscal;

    @Column(name = "nombre_corto", length = 50)
    private String nombreCorto;

    @Column(name = "rfc_taxid", length = 20, unique = true)
    private String rfcTaxid;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "email_contacto", length = 100)
    private String emailContacto;

    @Column(name = "nombre_contacto", length = 100)
    private String nombreContacto;     // persona de contacto en el proveedor

    @Column(name = "categoria", length = 60)
    private String categoria;           // ej: "Accesorios", "Equipos", "Servicios"

    @Column(name = "dias_credito", columnDefinition = "INT DEFAULT 0")
    private Integer diasCredito;

    @Column(name = "condiciones_garantia", columnDefinition = "TEXT")
    private String condicionesGarantia;

    @Column(name = "activo", columnDefinition = "TINYINT DEFAULT 1")
    private Boolean activo;

    @Version
    @Column(name = "version")
    private Integer version;
}