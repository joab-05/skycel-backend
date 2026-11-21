package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quién es el encargado actual de cada tienda y cuál es el taller. Antes cada empleado traía su tienda; ahora se designa aquí, en la
 * tienda: el encargado recibe la tienda como suya (de ahí salen sus permisos de tienda) y los técnicos trabajan en el taller.
 * Los vendedores y los administradores no tienen tienda fija: trabajan en la que indique el equipo.
 */
@Service
@RequiredArgsConstructor
public class TiendaService {

    private final TiendaRepository  tiendaRepository;
    private final UsuarioRepository usuarioRepository;

    /** Pone (o quita, con null) al encargado actual de la tienda. Quien deja de serlo ya no tiene tienda. */
    @Transactional
    public Tienda asignarEncargado(Integer codti, Integer idusuario) {
        Tienda tienda = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada: " + codti));

        Integer actualId = tienda.getIdEncargado();
        if (idusuario != null && idusuario.equals(actualId)) return conNombre(tienda);   // ya lo es

        Usuario nuevo = null;
        if (idusuario != null) {
            nuevo = usuarioRepository.findById(idusuario)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuario no encontrado: " + idusuario));
            if (!Boolean.TRUE.equals(nuevo.getActivo()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nuevo.getNombreCompleto() + " está desactivado.");
            if (nuevo.getRol() != Rol.ENCARGADO_TIENDA)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        nuevo.getNombreCompleto() + " no tiene el rol de encargado de tienda: cámbialo en Empleados y vuelve a elegirlo.");
            Optional<Tienda> otra = tiendaRepository.findFirstByIdEncargado(idusuario);
            if (otra.isPresent() && !otra.get().getCodti().equals(codti))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        nuevo.getNombreCompleto() + " ya es el encargado de " + otra.get().getNombre() + ": quítalo de allí primero.");
        }

        // el encargado anterior deja de tener tienda
        if (actualId != null) {
            usuarioRepository.findById(actualId).ifPresent(anterior -> {
                if (anterior.getTienda() != null && anterior.getTienda().getCodti().equals(codti)) {
                    anterior.setTienda(null);
                    usuarioRepository.save(anterior);
                }
            });
        }

        tienda.setIdEncargado(idusuario);
        if (nuevo != null) {
            nuevo.setTienda(tienda);
            usuarioRepository.save(nuevo);
        }
        return conNombre(tiendaRepository.save(tienda));
    }

    /**
     * Marca esta tienda como el taller (donde trabajan los técnicos) o le quita la marca. Hay un solo taller: marcarlo desmarca a los
     * demás y los técnicos pasan a trabajar en él.
     */
    @Transactional
    public Tienda marcarTaller(Integer codti, boolean esTaller) {
        Tienda tienda = tiendaRepository.findById(codti)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada: " + codti));
        if (esTaller == Boolean.TRUE.equals(tienda.getEsTaller())) return conNombre(tienda);
        if (esTaller) {
            for (Tienda otra : tiendaRepository.findByEsTallerTrue()) {
                otra.setEsTaller(false);
                tiendaRepository.save(otra);
            }
            tienda.setEsTaller(true);
            // los técnicos trabajan en el taller
            for (Usuario tecnico : usuarioRepository.findByRolAndActivoTrue(Rol.TECNICO)) {
                tecnico.setTienda(tienda);
                usuarioRepository.save(tecnico);
            }
        } else {
            tienda.setEsTaller(false);
        }
        return conNombre(tiendaRepository.save(tienda));
    }

    /** El taller de los técnicos (null si todavía no se marca ninguno). */
    @Transactional(readOnly = true)
    public Tienda tallerActual() {
        return tiendaRepository.findByEsTallerTrue().stream().findFirst().orElse(null);
    }

    /** Cuando alguien deja de ser encargado (cambia de rol o se desactiva) su tienda queda sin encargado. */
    @Transactional
    public void liberarComoEncargado(Usuario usuario) {
        tiendaRepository.findFirstByIdEncargado(usuario.getIdusuario()).ifPresent(t -> {
            t.setIdEncargado(null);
            tiendaRepository.save(t);
        });
        if (usuario.getRol() == Rol.ENCARGADO_TIENDA || usuario.getTienda() != null) usuario.setTienda(null);
    }

    // ── Nombres para mostrar ──────────────────────────────────────────────────

    public Tienda conNombre(Tienda t) {
        if (t.getIdEncargado() != null) {
            usuarioRepository.findById(t.getIdEncargado()).ifPresent(u -> t.setNombreEncargado(u.getNombreCompleto()));
        } else {
            t.setNombreEncargado(null);
        }
        return t;
    }

    public List<Tienda> conNombres(Collection<Tienda> tiendas) {
        Map<Integer, String> nombres = usuarioRepository.findAllById(
                        tiendas.stream().map(Tienda::getIdEncargado).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Usuario::getIdusuario, Usuario::getNombreCompleto, (a, b) -> a));
        tiendas.forEach(t -> t.setNombreEncargado(t.getIdEncargado() != null ? nombres.get(t.getIdEncargado()) : null));
        return List.copyOf(tiendas);
    }
}
