package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProveedorRequestDTO {
    @Size(max = 150, message = "El nombre fiscal no puede exceder 150 caracteres")
    private String nombreFiscal;

    @NotBlank(message = "El nombre corto es obligatorio")
    @Size(max = 50, message = "El nombre corto no puede exceder 50 caracteres")
    private String nombreCorto;

    @Size(max = 20, message = "El RFC/TaxID no puede exceder 20 caracteres")
    private String rfcTaxid;

    @Size(max = 20, message = "El teléfono no puede exceder 20 caracteres")
    private String telefono;

    @Size(max = 100, message = "El email no puede exceder 100 caracteres")
    private String emailContacto;

    private Integer diasCredito;

    private String condicionesGarantia;
}
