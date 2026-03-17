package com.skycel.backend.domain.dto.response;

import lombok.Data;

@Data
public class ProductoMasterResponseDTO {
    private Integer idprodmaster;
    private String nombreBase;
    private String especificaciones;
    private String notaAdicional;
    private CategoriaResponseDTO categoria;
    private Boolean activo;
}
