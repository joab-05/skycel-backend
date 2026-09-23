package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Notificacion;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.notificacion.NotificacionResponseDto;
import com.skycel.backend.repository.NotificacionRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Avisos por usuario (leído/no leído) de cambios en garantías, órdenes de servicio y traspasos.
 * Los servicios de esos módulos llaman a {@link #notificarTienda} / {@link #notificarRol} en el punto
 * donde ya cambian el estado; aquí solo se resuelve a quién le toca y se guarda una fila por destinatario.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    // ── Generar (llamado desde otros servicios) ─────────────────────────────────

    /** Notifica a todo el personal activo de una tienda (p. ej. avance de una garantía o de una orden). */
    @Transactional
    public void notificarTienda(Integer codti, String tipo, String titulo, String ruta) {
        for (Usuario u : usuarioRepository.findByTienda_CodtiAndActivoTrue(codti)) {
            crear(u, tipo, titulo, ruta);
        }
    }

    /** Notifica a todos los usuarios activos de un rol (p. ej. a los técnicos cuando llega un equipo nuevo). */
    @Transactional
    public void notificarRol(Rol rol, String tipo, String titulo, String ruta) {
        for (Usuario u : usuarioRepository.findByRolAndActivoTrue(rol)) {
            crear(u, tipo, titulo, ruta);
        }
    }

    private void crear(Usuario destino, String tipo, String titulo, String ruta) {
        notificacionRepository.save(Notificacion.builder()
                .usuarioDestino(destino).tipo(tipo).titulo(titulo).ruta(ruta).leida(false).build());
    }

    // ── Consultar / marcar leída (API para la web) ──────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificacionResponseDto> listarPendientes(String username) {
        Usuario u = usuarioPorUsername(username);
        return notificacionRepository.findByUsuarioDestino_IdusuarioAndLeidaFalseOrderByFechaCreacionDesc(u.getIdusuario())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponseDto> listarTodas(String username) {
        Usuario u = usuarioPorUsername(username);
        return notificacionRepository.findTop100ByUsuarioDestino_IdusuarioOrderByFechaCreacionDesc(u.getIdusuario())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long contarPendientes(String username) {
        Usuario u = usuarioPorUsername(username);
        return notificacionRepository.countByUsuarioDestino_IdusuarioAndLeidaFalse(u.getIdusuario());
    }

    @Transactional
    public void marcarLeida(Integer id, String username) {
        Usuario u = usuarioPorUsername(username);
        Notificacion n = notificacionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada: " + id));
        if (!n.getUsuarioDestino().getIdusuario().equals(u.getIdusuario())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa notificación no es tuya.");
        }
        if (!Boolean.TRUE.equals(n.getLeida())) {
            n.setLeida(true);
            n.setFechaLeida(LocalDateTime.now());
            notificacionRepository.save(n);
        }
    }

    @Transactional
    public void marcarTodasLeidas(String username) {
        Usuario u = usuarioPorUsername(username);
        List<Notificacion> pendientes = notificacionRepository
                .findByUsuarioDestino_IdusuarioAndLeidaFalseOrderByFechaCreacionDesc(u.getIdusuario());
        LocalDateTime ahora = LocalDateTime.now();
        for (Notificacion n : pendientes) {
            n.setLeida(true);
            n.setFechaLeida(ahora);
        }
        notificacionRepository.saveAll(pendientes);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    private NotificacionResponseDto toDto(Notificacion n) {
        return NotificacionResponseDto.builder()
                .idnotificacion(n.getIdnotificacion())
                .tipo(n.getTipo())
                .titulo(n.getTitulo())
                .ruta(n.getRuta())
                .leida(n.getLeida())
                .fechaCreacion(n.getFechaCreacion())
                .build();
    }
}
