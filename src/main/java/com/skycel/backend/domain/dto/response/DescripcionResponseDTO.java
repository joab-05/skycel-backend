package com.skycel.backend.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DescripcionResponseDTO {
    private Integer id;
    private Short idCategoria;
    private String nombre;
}
