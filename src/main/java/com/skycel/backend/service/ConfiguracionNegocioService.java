package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ConfiguracionNegocio;
import com.skycel.backend.dto.configuracion.ConfiguracionNegocioDto;
import com.skycel.backend.repository.ConfiguracionNegocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos del negocio compartidos por todas las tiendas. Si nunca se han capturado, se crean con valores por omisión. */
@Service
@RequiredArgsConstructor
public class ConfiguracionNegocioService {

    public static final int DIAS_DEVOLUCION_POR_OMISION = 15;

    private final ConfiguracionNegocioRepository repository;

    @Transactional
    public ConfiguracionNegocioDto obtener() {
        return toDto(cargar());
    }

    /** Días que una venta admite devoluciones. */
    @Transactional
    public int diasDevolucion() {
        Integer dias = cargar().getDiasDevolucion();
        return dias != null ? dias : DIAS_DEVOLUCION_POR_OMISION;
    }

    /** Guarda los datos del negocio (solo ROOT/ADMIN, lo exige el controlador). */
    @Transactional
    public ConfiguracionNegocioDto actualizar(ConfiguracionNegocioDto dto, String username) {
        ConfiguracionNegocio c = cargar();
        c.setNombreComercial(dto.getNombreComercial().trim());
        c.setRazonSocial(limpio(dto.getRazonSocial()));
        c.setRfc(dto.getRfc() == null || dto.getRfc().isBlank() ? null : dto.getRfc().trim().toUpperCase());
        c.setEmail(limpio(dto.getEmail()));
        c.setSitioWeb(limpio(dto.getSitioWeb()));
        c.setTicketPie(limpio(dto.getTicketPie()));
        c.setTicketLeyenda(limpio(dto.getTicketLeyenda()));
        c.setDiasDevolucion(dto.getDiasDevolucion());
        c.setActualizadoPor(username);
        return toDto(repository.save(c));
    }

    /** Tamaño máximo del archivo que se sube y lado máximo del logo guardado. */
    public static final int LOGO_MAX_BYTES = 2 * 1024 * 1024;
    public static final int LOGO_LADO_MAX = 512;

    /** El logo guardado (PNG), si hay uno. */
    @Transactional(readOnly = true)
    public java.util.Optional<byte[]> logo() {
        return repository.findById(ConfiguracionNegocio.ID_UNICO).map(ConfiguracionNegocio::getLogo).filter(b -> b != null && b.length > 0);
    }

    @Transactional(readOnly = true)
    public long logoVersion() {
        return repository.findById(ConfiguracionNegocio.ID_UNICO)
                .filter(c -> c.getLogo() != null && c.getLogo().length > 0).map(c -> c.getLogoVersion() != null ? c.getLogoVersion() : 0L).orElse(0L);
    }

    /**
     * Guarda el logo: debe ser una imagen PNG o JPEG de hasta 2 MB. Se reduce a 512 px por lado si es más grande y se guarda
     * como PNG (conserva la transparencia), así todas las aplicaciones reciben el mismo archivo ligero.
     */
    @Transactional
    public long guardarLogo(byte[] datos, String username) {
        if (datos == null || datos.length == 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "No se recibió ninguna imagen.");
        }
        if (datos.length > LOGO_MAX_BYTES) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "La imagen pesa más de 2 MB. Usa una más ligera.");
        }
        java.awt.image.BufferedImage origen;
        try {
            origen = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(datos));
        } catch (java.io.IOException e) {
            origen = null;
        }
        if (origen == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El archivo no es una imagen PNG o JPEG válida.");
        }
        int mayor = Math.max(origen.getWidth(), origen.getHeight());
        double escala = mayor > LOGO_LADO_MAX ? (double) LOGO_LADO_MAX / mayor : 1.0;
        int ancho = Math.max(1, (int) Math.round(origen.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(origen.getHeight() * escala));
        java.awt.image.BufferedImage destino = new java.awt.image.BufferedImage(ancho, alto, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = destino.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(origen, 0, 0, ancho, alto, null);
        g.dispose();
        java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(destino, "png", salida);
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo procesar la imagen.");
        }
        ConfiguracionNegocio c = cargar();
        c.setLogo(salida.toByteArray());
        c.setLogoVersion(System.currentTimeMillis());
        c.setActualizadoPor(username);
        repository.save(c);
        return c.getLogoVersion();
    }

    /** Quita el logo: cada aplicación vuelve a su ícono por omisión. */
    @Transactional
    public void quitarLogo(String username) {
        ConfiguracionNegocio c = cargar();
        c.setLogo(null);
        c.setLogoVersion(System.currentTimeMillis());
        c.setActualizadoPor(username);
        repository.save(c);
    }

    private ConfiguracionNegocio cargar() {
        return repository.findById(ConfiguracionNegocio.ID_UNICO).orElseGet(() -> repository.save(ConfiguracionNegocio.builder()
                .id(ConfiguracionNegocio.ID_UNICO).nombreComercial("Skycel Tecnologías")
                .ticketPie("¡Gracias por su compra!").diasDevolucion(DIAS_DEVOLUCION_POR_OMISION).build()));
    }

    private String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private ConfiguracionNegocioDto toDto(ConfiguracionNegocio c) {
        return ConfiguracionNegocioDto.builder()
                .nombreComercial(c.getNombreComercial()).razonSocial(c.getRazonSocial()).rfc(c.getRfc())
                .email(c.getEmail()).sitioWeb(c.getSitioWeb()).ticketPie(c.getTicketPie()).ticketLeyenda(c.getTicketLeyenda())
                .diasDevolucion(c.getDiasDevolucion() != null ? c.getDiasDevolucion() : DIAS_DEVOLUCION_POR_OMISION)
                .fechaActualizacion(c.getFechaActualizacion()).actualizadoPor(c.getActualizadoPor()).build();
    }
}
