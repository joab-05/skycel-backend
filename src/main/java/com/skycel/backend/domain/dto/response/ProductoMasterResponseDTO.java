package com.skycel.backend.domain.dto.response;

import lombok.Data;

@Data
public class ProductoMasterResponseDTO {
    private Integer idprodmaster;
    private String nombreBase;
    private String marca;            // solo EQUIPO
    private String modelo;           // solo EQUIPO
    private String especificaciones; // "Descripción" — ACCESORIO/SERVICIO
    private String notaAdicional;    // "Descripción 2" — solo SERVICIO
    private CategoriaResponseDTO categoria;
    private String tipo;
    private Boolean activo;
}
