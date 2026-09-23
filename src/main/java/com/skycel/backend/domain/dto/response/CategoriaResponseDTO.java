package com.skycel.backend.domain.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class CategoriaResponseDTO {
    private Short idcat;
    private String nombre;
    private String codigo;
    private String tipo; // CELULAR, ACCESORIO, SERVICIO, TABLET
    private Short idCategoriaSuperior; // Avoid circular reference, just send ID
    private List<CategoriaResponseDTO> subcategorias;
}
