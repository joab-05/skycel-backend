package com.skycel.backend.domain.dto.response;

import lombok.Data;

@Data
public class ProveedorResponseDTO {
    private Short   id;
    private String  nombreFiscal;
    private String  nombreCorto;
    private String  rfcTaxid;
    private String  telefono;
    private String  emailContacto;
    private String  nombreContacto;
    private String  categoria;
    private Integer diasCredito;
    private String  condicionesGarantia;
    private Boolean activo;
}