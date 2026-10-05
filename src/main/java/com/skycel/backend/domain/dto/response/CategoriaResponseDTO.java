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
    private Boolean incluirEnNombre; // si su nombre entra en el nombre completo de los artículos
    private Integer nivel;           // 1 = principal, 2 = subcategoría 1, 3 = subcategoría 2
    private List<CategoriaResponseDTO> subcategorias;
}
