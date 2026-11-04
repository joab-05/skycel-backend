package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EquipoJSystem;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.repository.EquipoJSystemRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seguimiento de las computadoras con JSystem: cada una reporta cada pocos minutos su versión y cómo va la actualización,
 * para que un administrador vea de un vistazo cuáles están desactualizadas, con error o sin reportar.
 */
@Service
@RequiredArgsConstructor
public class EquipoJSystemService {

    /** Sin reportar en este tiempo, la computadora se considera apagada o sin conexión. */
    static final Duration SIN_REPORTAR = Duration.ofHours(24);

    private final EquipoJSystemRepository repository;
    private final UsuarioRepository usuarioRepository;
    @Value("${skycel.jsystem.carpeta:./jsystem}")
    private String carpetaPublicada;

    /** Lo que manda cada computadora. */
    public record Latido(String host, String nombreEquipo, String version, String versionPendiente, String estadoActualizacion, Boolean conError) {}

    public enum Situacion { AL_DIA, ESPERA_REINICIO, DESACTUALIZADA, CON_ERROR, SIN_REPORTAR }

    public record EquipoDto(String host, String nombreEquipo, String usuario, String nombreTienda, String version, String versionPendiente,
                            String estadoActualizacion, LocalDateTime ultimaConexion, Situacion situacion) {}

    @Transactional
    public void registrar(Latido l, String username) {
        String host = recortar(l.host(), 80);
        if (host == null) return;
        EquipoJSystem e = repository.findByHost(host).orElseGet(() -> EquipoJSystem.builder().host(host).build());
        e.setNombreEquipo(recortar(l.nombreEquipo(), 80));
        e.setVersion(recortar(l.version(), 30));
        e.setVersionPendiente(recortar(l.versionPendiente(), 30));
        e.setEstadoActualizacion(recortar(l.estadoActualizacion(), 300));
        e.setConError(Boolean.TRUE.equals(l.conError()));
        e.setUltimaConexion(LocalDateTime.now());
        Usuario u = username == null ? null : usuarioRepository.findByUsername(username).orElse(null);
        if (u != null) {
            e.setUsuario(u.getNombreCompleto() != null ? u.getNombreCompleto() : u.getUsername());
            e.setCodti(u.getTienda() != null ? u.getTienda().getCodti() : null);
            e.setNombreTienda(u.getTienda() != null ? u.getTienda().getNombre() : null);
        }
        repository.save(e);
    }

    @Transactional(readOnly = true)
    public List<EquipoDto> listar() {
        String publicada = versionPublicada();
        LocalDateTime ahora = LocalDateTime.now();
        return repository.findAllByOrderByUltimaConexionDesc().stream()
                .map(e -> new EquipoDto(e.getHost(), e.getNombreEquipo(), e.getUsuario(), e.getNombreTienda(), e.getVersion(), e.getVersionPendiente(),
                        e.getEstadoActualizacion(), e.getUltimaConexion(), situacion(e, publicada, ahora)))
                .toList();
    }

    static Situacion situacion(EquipoJSystem e, String publicada, LocalDateTime ahora) {
        if (e.getUltimaConexion() == null || Duration.between(e.getUltimaConexion(), ahora).compareTo(SIN_REPORTAR) > 0) return Situacion.SIN_REPORTAR;
        boolean atrasada = publicada != null && numero(e.getVersion()) < numero(publicada);
        if (Boolean.TRUE.equals(e.getConError()) && atrasada) return Situacion.CON_ERROR;
        if (!atrasada) return Situacion.AL_DIA;
        return e.getVersionPendiente() != null && !e.getVersionPendiente().isBlank() ? Situacion.ESPERA_REINICIO : Situacion.DESACTUALIZADA;
    }

    /** La versión que el servidor publica para las computadoras, o null si no hay. */
    String versionPublicada() {
        try {
            Path ver = Paths.get(carpetaPublicada).resolve("version.txt");
            return Files.isRegularFile(ver) ? Files.readString(ver, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private static long numero(String v) {
        try { return Long.parseLong(v == null ? "0" : v.replaceAll("\\D", "")); } catch (NumberFormatException ex) { return 0; }
    }

    private static String recortar(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
