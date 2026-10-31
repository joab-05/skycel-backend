package com.skycel.backend.shared.qr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Dibuja un código QR como imagen PNG en blanco y negro, con el margen (zona silenciosa) que piden los lectores. */
public final class QrPng {

    private QrPng() {}

    public static byte[] png(String texto, int modulo, int margen) throws IOException {
        QrCode qr = QrCode.codificar(texto);
        int lado = (qr.tamano() + margen * 2) * modulo;
        BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < lado; y++) {
            for (int x = 0; x < lado; x++) {
                boolean oscuro = qr.modulo(x / modulo - margen, y / modulo - margen);
                img.setRGB(x, y, oscuro ? 0x000000 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
