package com.skycel.backend.controller;

import com.skycel.backend.shared.qr.QrCode;
import com.skycel.backend.shared.qr.QrPng;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Imagen QR del enlace de seguimiento de una orden (para el comprobante impreso desde la web). Solo dibuja direcciones de este mismo
 * servidor: no sirve para generar códigos de cualquier cosa.
 */
@RestController
@RequestMapping("/api/publico/qr")
@Tag(name = "Seguimiento", description = "Código QR del enlace de seguimiento")
public class PublicoQrController {

    private final String base;

    public PublicoQrController(@Value("${skycel.publico.url:https://skycelsys.ddns.net}") String base) {
        this.base = base;
    }

    @Operation(summary = "Imagen PNG del código QR de un enlace de seguimiento")
    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@RequestParam("t") String texto, @RequestParam(value = "px", defaultValue = "6") int px) throws IOException {
        if (texto == null || texto.length() > 180 || !texto.startsWith(base + "/seguimiento/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se genera el QR de un enlace de seguimiento de este sistema.");
        int modulo = Math.max(2, Math.min(px, 12));
        try {
            QrCode.codificar(texto);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.IMAGE_PNG).body(QrPng.png(texto, modulo, 4));
    }
}
