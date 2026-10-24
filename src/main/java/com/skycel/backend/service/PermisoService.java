package com.skycel.backend.service;

import com.skycel.backend.domain.entity.PermisoRol;
import com.skycel.backend.domain.enums.Permiso;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.PermisoRolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Qué puede hacer cada rol. Los endpoints se protegen con {@code @PreAuthorize("@permisos.tiene('CODIGO')")} (el bean se
 * llama {@code permisos}); root cambia las asignaciones desde JSystem. ROOT siempre puede todo.
 *
 * <p>Las asignaciones se guardan en la base y se leen una vez a memoria: cada petición solo consulta ese mapa.
 */
@Service("permisos")
@RequiredArgsConstructor
public class PermisoService {

    private static final String ROLE_ = "ROLE_";

    private final PermisoRolRepository permisoRolRepository;

    /** código del permiso → roles que lo tienen (sin ROOT, que siempre lo tiene). */
    private volatile Map<String, Set<Rol>> mapa = Map.of();

    // ── Arranque ─────────────────────────────────────────────────────────────

    /** Siembra los permisos que falten con sus roles iniciales. Nunca toca lo que root ya cambió. */
    @Transactional
    public void sembrar() {
        Set<String> sembrados = permisoRolRepository.findAll().stream()
                .filter(p -> PermisoRol.MARCA_SEMBRADO.equals(p.getRol()))
                .map(PermisoRol::getPermiso)
                .collect(Collectors.toSet());
        for (Permiso p : Permiso.values()) {
            if (sembrados.contains(p.name())) continue;
            permisoRolRepository.save(PermisoRol.builder().permiso(p.name()).rol(PermisoRol.MARCA_SEMBRADO).build());
            for (Rol r : p.porOmision()) {
                permisoRolRepository.save(PermisoRol.builder().permiso(p.name()).rol(r.name()).build());
            }
        }
        recargar();
    }

    /** Vuelve a leer las asignaciones de la base. */
    public void recargar() {
        Map<String, Set<Rol>> nuevo = new HashMap<>();
        for (PermisoRol fila : permisoRolRepository.findAll()) {
            if (PermisoRol.MARCA_SEMBRADO.equals(fila.getRol())) continue;
            try {
                nuevo.computeIfAbsent(fila.getPermiso(), k -> EnumSet.noneOf(Rol.class)).add(Rol.valueOf(fila.getRol()));
            } catch (IllegalArgumentException e) {
                // un rol que ya no existe: se ignora
            }
        }
        mapa = nuevo;
    }

    // ── Consulta (la usa @PreAuthorize en cada petición) ─────────────────────

    /** ¿Quien hace la petición tiene este permiso? */
    public boolean tiene(String codigo) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return false;
        for (GrantedAuthority g : a.getAuthorities()) {
            String nombre = g.getAuthority();
            if (nombre == null || !nombre.startsWith(ROLE_)) continue;
            try {
                if (tiene(Rol.valueOf(nombre.substring(ROLE_.length())), codigo)) return true;
            } catch (IllegalArgumentException e) {
                // rol desconocido
            }
        }
        return false;
    }

    public boolean tiene(Rol rol, String codigo) {
        if (rol == Rol.ROOT) return true;
        Set<Rol> roles = mapa.get(codigo);
        return roles != null && roles.contains(rol);
    }

    /** Los códigos de todos los permisos de un rol (ROOT: todos). */
    public List<String> deRol(Rol rol) {
        return Arrays.stream(Permiso.values()).map(Permiso::name).filter(c -> tiene(rol, c)).collect(Collectors.toList());
    }

    // ── Administración (solo root) ───────────────────────────────────────────

    /** Todos los permisos con la etiqueta, el grupo y los roles que hoy los tienen. */
    public Map<String, Object> catalogo() {
        List<Map<String, Object>> permisos = new ArrayList<>();
        for (Permiso p : Permiso.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo", p.name());
            m.put("etiqueta", p.etiqueta());
            m.put("grupo", p.grupo());
            m.put("roles", Permiso.ROLES_CONFIGURABLES.stream().filter(r -> tiene(r, p.name())).map(Rol::name).sorted().collect(Collectors.toList()));
            permisos.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("roles", ordenados(Permiso.ROLES_CONFIGURABLES));
        r.put("permisos", permisos);
        return r;
    }

    /** Deja exactamente estos roles con el permiso. */
    @Transactional
    public Map<String, Object> asignar(String codigo, Collection<String> roles) {
        Permiso permiso;
        try {
            permiso = Permiso.valueOf(codigo);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el permiso '" + codigo + "'.");
        }
        Set<Rol> nuevos = EnumSet.noneOf(Rol.class);
        for (String nombre : roles == null ? List.<String>of() : roles) {
            Rol rol;
            try {
                rol = Rol.valueOf(nombre);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol no válido: " + nombre);
            }
            if (!Permiso.ROLES_CONFIGURABLES.contains(rol)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        rol == Rol.ROOT ? "ROOT siempre tiene todos los permisos: no se configura." : "El rol " + rol + " no se configura aquí.");
            }
            nuevos.add(rol);
        }
        permisoRolRepository.deleteByPermisoAndRolNot(permiso.name(), PermisoRol.MARCA_SEMBRADO);
        permisoRolRepository.flush();
        for (Rol rol : nuevos) {
            permisoRolRepository.save(PermisoRol.builder().permiso(permiso.name()).rol(rol.name()).build());
        }
        recargar();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", permiso.name());
        m.put("roles", ordenados(nuevos));
        return m;
    }

    private static List<String> ordenados(Collection<Rol> roles) {
        return roles.stream().sorted().map(Rol::name).collect(Collectors.toList());
    }
}
