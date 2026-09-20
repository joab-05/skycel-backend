package com.skycel.backend.security;

import com.skycel.backend.domain.entity.UsuarioSesion;
import com.skycel.backend.repository.UsuarioSesionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** La sesión del usuario que hace la petición en curso (para dejar constancia de ella en los movimientos). */
@Component
@RequiredArgsConstructor
public class SesionActual {

    private final UsuarioSesionRepository usuarioSesionRepository;

    /** La sesión autenticada, o null si no hay petición autenticada (procesos internos, pruebas). */
    public UsuarioSesion actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof SesionWebDetails d && d.getIdsesion() != null) {
            return usuarioSesionRepository.getReferenceById(d.getIdsesion());
        }
        return null;
    }
}
