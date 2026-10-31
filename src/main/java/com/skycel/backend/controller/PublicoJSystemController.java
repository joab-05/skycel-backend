package com.skycel.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actualizaciones automáticas de JSystem: el servidor publica la versión más nueva (carpeta con {@code JSystem.jar} y
 * {@code version.txt}) y cada equipo la revisa al abrir, la baja, comprueba que no esté dañada y la instala al cerrar el programa.
 */
@RestController
@RequestMapping("/api/publico/jsystem")
@Tag(name = "Actualizaciones", description = "Versión más nueva de JSystem para los equipos de las tiendas")
public class PublicoJSystemController {

    private final Path carpeta;
    private volatile String huellaCache, huellaDe;

    public PublicoJSystemController(@Value("${skycel.jsystem.carpeta:./jsystem}") String carpeta) {
        this.carpeta = Paths.get(carpeta);
    }

    @Operation(summary = "Versión más nueva de JSystem", description = "Sin sesión: número de versión, tamaño y SHA-256 del programa publicado.")
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> version() throws IOException {
        Path jar = carpeta.resolve("JSystem.jar"), ver = carpeta.resolve("version.txt");
        Map<String, Object> m = new LinkedHashMap<>();
        if (!Files.isRegularFile(jar) || !Files.isRegularFile(ver)) {
            m.put("disponible", false);
            return ResponseEntity.ok(m);
        }
        m.put("disponible", true);
        m.put("version", Files.readString(ver, StandardCharsets.UTF_8).trim());
        m.put("bytes", Files.size(jar));
        m.put("sha256", huella(jar));
        return ResponseEntity.ok(m);
    }

    @Operation(summary = "Descarga el programa JSystem más nuevo")
    @GetMapping("/JSystem.jar")
    public ResponseEntity<Resource> descargar() {
        Path jar = carpeta.resolve("JSystem.jar");
        if (!Files.isRegularFile(jar)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"JSystem.jar\"")
                .body(new FileSystemResource(jar));
    }

    /** El SHA-256 solo se vuelve a calcular si el archivo cambió. */
    private synchronized String huella(Path jar) throws IOException {
        String marca = Files.getLastModifiedTime(jar).toMillis() + ":" + Files.size(jar);
        if (marca.equals(huellaDe) && huellaCache != null) return huellaCache;
        try (InputStream in = Files.newInputStream(jar)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            huellaCache = HexFormat.of().formatHex(md.digest());
            huellaDe = marca;
            return huellaCache;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
