package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.entity.UsuarioSesion;
import com.skycel.backend.dto.auth.LoginRequestDto;
import com.skycel.backend.dto.auth.LoginResponseDto;
import com.skycel.backend.repository.UsuarioSesionRepository;
import com.skycel.backend.security.JwtUtil;
import com.skycel.backend.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UsuarioSesionRepository usuarioSesionRepository;

    @Transactional
    public LoginResponseDto authenticate(LoginRequestDto requestDto, HttpServletRequest request) {
        // Autentica internamente con Spring Security usando BCrypt (Falla con Exception si no existe/no coincide)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(requestDto.getUsername(), requestDto.getPassword())
        );

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Usuario usuario = userDetails.getUsuario();

        // Generamos un UUID único para encapsularlo en el JWT
        String tokenUuid = UUID.randomUUID().toString();

        // Registramos la sesión física en MySQL (Para poder denegar remotamente después si es necesario)
        UsuarioSesion sesion = UsuarioSesion.builder()
                .usuario(usuario)
                .tiendaTerminal(usuario.getTienda()) // Asumiremos que el login se hace desde la tienda default del usuario, o un valor enviado por la app. 
                .tokenUuid(tokenUuid)
                .ipPublica(getClientIP(request))
                .userAgent(request.getHeader("User-Agent"))
                .dispositivoInfo("WEB-BROWSER") // Provisional, luego podrá pasarlo la GUI JavaFX/Movil
                .build();
        usuarioSesionRepository.save(sesion);

        // Fabricamos el Token final pasándole el UUID
        String jwtToken = jwtUtil.generateToken(userDetails, tokenUuid);

        return LoginResponseDto.builder()
                .token(jwtToken)
                .username(usuario.getUsername())
                .nombreCompleto(usuario.getNombreCompleto())
                .rol(usuario.getRol().name())
                .codti(usuario.getTienda().getCodti())
                .build();
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
