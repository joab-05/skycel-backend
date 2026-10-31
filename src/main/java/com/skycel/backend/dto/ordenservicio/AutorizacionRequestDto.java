package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** La respuesta del cliente a la cotización: quien la registra dice cómo la dio (en persona, llamada o WhatsApp). */
@Data
public class AutorizacionRequestDto {

    @NotNull(message = "Indique si el cliente autorizó o no")
    private Boolean autorizada;

    @NotBlank(message = "Indique cómo respondió el cliente (en persona, llamada o WhatsApp)")
    private String medio;

    private String nota;
}
