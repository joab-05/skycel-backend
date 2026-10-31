package com.skycel.backend.service;

import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** ¿Quien hace la petición puede cambiar los permisos? Root siempre; otro usuario solo si root le dio esa facultad. */
@Component("adminPermisos")
@RequiredArgsConstructor
public class AdminPermisos {

    private final UsuarioRepository usuarioRepository;

    public boolean puede() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return false;
        if (a.getAuthorities().stream().anyMatch(g -> "ROLE_ROOT".equals(g.getAuthority()))) return true;
        return usuarioRepository.findByUsername(a.getName())
                .map(u -> Boolean.TRUE.equals(u.getAdministraPermisos()) && Boolean.TRUE.equals(u.getActivo())).orElse(false);
    }
}
