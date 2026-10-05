package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoriaRequestDTO {

    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 50, message = "El nombre no puede exceder los 50 caracteres")
    private String nombreCat;

    // CELULAR, ACCESORIO, SERVICIO o TABLET: el comportamiento de los artículos del árbol. Solo se pide en la
    // categoría principal; las subcategorías lo heredan de ella (el valor que envíen se ignora).
    private String tipo;

    // Si el nombre de esta categoría entra en el nombre completo de los artículos (por defecto: sí en
    // subcategorías, no en la categoría principal).
    private Boolean incluirEnNombre;

    private Short idCategoriaSuperior;

    // Código corto (ej. "AUD") usado como prefijo del código autogenerado de accesorios
    // en esta subcategoría. Opcional al crear — solo se exige al momento de registrar
    // un accesorio cuyo código se deje en blanco (ver ProductoService.generarCodigoAccesorio).
    @Size(max = 10, message = "El código no puede exceder los 10 caracteres")
    private String codigo;

    // We do NOT expose idcat (Auto-generated), activo (Always true on create), or audit fields
}
