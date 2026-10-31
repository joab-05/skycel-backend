package com.skycel.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Los textos de WhatsApp para el cliente y el armado del teléfono. */
class OrdenAvisosTest {

    private OrdenAvisos.Datos datos() {
        return new OrdenAvisos.Datos("Skycel", "Zócalo", "7571234567", "Calle Hidalgo 12", "SOFIA RAMIREZ", "Samsung A54", "OS-000123",
                "pantalla rota y batería hinchada", new BigDecimal("1450"), new BigDecimal("1200"), new BigDecimal("300"), new BigDecimal("1150"),
                LocalDateTime.of(2026, 11, 3, 17, 30), null, LocalDateTime.of(2026, 11, 2, 12, 0), 30, LocalDate.of(2026, 12, 2),
                "https://skycelsys.ddns.net/seguimiento/?f=OS-000123");
    }

    @Test
    @DisplayName("teléfono: 10 dígitos o 52 + 10 sirven; con 521 se normaliza; otra cosa no sirve")
    void telefono() {
        assertThat(OrdenAvisos.telefonoWhatsApp("757 120 0001")).isEqualTo("527571200001");
        assertThat(OrdenAvisos.telefonoWhatsApp("+52 757-120-0001")).isEqualTo("527571200001");
        assertThat(OrdenAvisos.telefonoWhatsApp("+521 7571200001")).isEqualTo("527571200001");
        assertThat(OrdenAvisos.telefonoWhatsApp("12345")).isNull();
        assertThat(OrdenAvisos.telefonoWhatsApp(null)).isNull();
    }

    @Test
    @DisplayName("cada aviso dice lo importante: folio, montos y a dónde ir")
    void textos() {
        OrdenAvisos.Datos d = datos();
        assertThat(OrdenAvisos.texto(OrdenAvisos.Tipo.RECIBIDA, d)).startsWith("Hola Sofia, te saludamos de Skycel (Zócalo)")
                .contains("*OS-000123*").contains("anticipo de $300").contains("seguimiento/?f=OS-000123").contains("5:30");
        assertThat(OrdenAvisos.texto(OrdenAvisos.Tipo.COTIZACION, d)).contains("$1,450").contains("SÍ").contains("pantalla rota")
                .contains("te quedan por pagar $1,150");
        assertThat(OrdenAvisos.texto(OrdenAvisos.Tipo.PRECIO_CAMBIO, d)).contains("antes eran $1,200 y ahora son *$1,450*");
        assertThat(OrdenAvisos.texto(OrdenAvisos.Tipo.LISTA, d)).contains("listo").contains("Queda por pagar $1,150")
                .contains("Calle Hidalgo 12").contains("tel. 7571234567");
        assertThat(OrdenAvisos.texto(OrdenAvisos.Tipo.RECORDATORIO, d)).contains("sigue listo");
        assertThat(OrdenAvisos.texto(OrdenAvisos.Tipo.ENTREGADA, d)).contains("garantía de 30 días").contains("2 de diciembre de 2026");
    }

    @Test
    @DisplayName("el enlace de WhatsApp codifica el texto con %20 y acentos")
    void enlace() {
        String url = OrdenAvisos.enlaceWhatsApp("527571200001", "Hola, ¿qué tal?");
        assertThat(url).isEqualTo("https://wa.me/527571200001?text=Hola%2C%20%C2%BFqu%C3%A9%20tal%3F");
    }
}
