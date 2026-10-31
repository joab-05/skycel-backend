package com.skycel.backend.shared.qr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrCodeTest {

    @Test
    @DisplayName("el tamaño crece con el texto y un texto enorme se rechaza")
    void tamano() {
        assertThat(QrCode.codificar("OS-1").tamano()).isEqualTo(21);                    // versión 1
        assertThat(QrCode.codificar("x".repeat(60)).tamano()).isGreaterThan(21);
        assertThatThrownBy(() -> QrCode.codificar("x".repeat(400))).isInstanceOf(IllegalArgumentException.class);
    }

    /** Genera imágenes en la carpeta que indique -Dqr.salida para comprobarlas con un lector externo; sin esa opción no hace nada. */
    @Test
    @DisplayName("genera imágenes de ejemplo para comprobarlas con un lector externo (opcional)")
    void imagenesDeEjemplo() throws Exception {
        String salida = System.getProperty("qr.salida");
        if (salida == null) return;
        Path dir = Paths.get(salida);
        Files.createDirectories(dir);
        for (int n : new int[]{3, 20, 38, 41, 42, 61, 62, 84, 100, 130, 150, 190}) {
            String texto = ("https://skycelsys.ddns.net/seguimiento/?f=OS-000123&" + "abcdefghij0123456789".repeat(12)).substring(0, n);
            Files.write(dir.resolve("qr-" + n + ".png"), QrPng.png(texto, 6, 4));
            Files.writeString(dir.resolve("qr-" + n + ".txt"), texto);
        }
    }
}
