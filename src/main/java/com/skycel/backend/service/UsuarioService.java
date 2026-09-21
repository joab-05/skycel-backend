package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.usuario.UsuarioCreateDto;
import com.skycel.backend.dto.usuario.UsuarioResponseDto;
import com.skycel.backend.dto.usuario.UsuarioUpdateDto;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository        usuarioRepository;
    private final EmpleadoPerfilRepository empleadoPerfilRepository;
    private final TiendaRepository         tiendaRepository;
    private final PasswordEncoder          passwordEncoder;

    // ── Listar ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UsuarioResponseDto> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Técnicos activos (rol TECNICO); con {@code codti}, solo los de esa tienda. Para asignarlos a una orden de servicio. */
    @Transactional(readOnly = true)
    public List<UsuarioResponseDto> listarTecnicos(Integer codti, String username) {
        Usuario quien = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        if (quien.getRol() == Rol.TECNICO) {
            // solo el técnico encargado, y de su tienda
            if (!Boolean.TRUE.equals(quien.getTecnicoEncargado())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el técnico encargado consulta la lista de técnicos.");
            }
            // el taller atiende a todas las sucursales: ve a todos los técnicos
        }
        final Integer tienda = codti;
        return usuarioRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .filter(u -> u.getRol() == com.skycel.backend.domain.enums.Rol.TECNICO)
                .filter(u -> tienda == null || (u.getTienda() != null && u.getTienda().getCodti().equals(tienda)))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDto> listarActivos() {
        return usuarioRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDto obtenerPorId(Integer id) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado: " + id));
        return toDto(u);
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Transactional
    public UsuarioResponseDto crear(UsuarioCreateDto dto) {

        // Validar username único
        if (usuarioRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El username '" + dto.getUsername() + "' ya está en uso.");
        }

        // Validar tienda
        Tienda tienda = tiendaRepository.findById(dto.getCodti())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Tienda no encontrada: " + dto.getCodti()));

        // Parsear rol
        Rol rol = parseRol(dto.getRol());

        // Crear usuario
        Usuario usuario = Usuario.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nombreCompleto(dto.getNombreCompleto())
                .tienda(tienda)
                .rol(rol)
                .tecnicoEncargado(rol == Rol.TECNICO && Boolean.TRUE.equals(dto.getTecnicoEncargado()))
                .telefono(dto.getTelefono())
                .email(dto.getEmail())
                .activo(true)
                .build();

        usuario = usuarioRepository.save(usuario);

        // Crear perfil de empleado
        EmpleadoPerfil perfil = EmpleadoPerfil.builder()
                .usuario(usuario)
                .sueldoBase(dto.getSueldoBase() != null ? dto.getSueldoBase() : BigDecimal.ZERO)
                .fechaIngreso(dto.getFechaIngreso() != null ? dto.getFechaIngreso() : LocalDate.now())
                .limiteFondoInventario(dto.getLimiteFondoInventario() != null
                        ? dto.getLimiteFondoInventario() : BigDecimal.ZERO)
                .acumuladoFondoInventario(BigDecimal.ZERO)
                .activo(true)
                .build();

        empleadoPerfilRepository.save(perfil);

        return toDto(usuario);
    }

    // ── Editar ────────────────────────────────────────────────────────────────

    @Transactional
    public UsuarioResponseDto actualizar(Integer id, UsuarioUpdateDto dto) {

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado: " + id));

        // Actualizar campos del usuario solo si vienen en el DTO
        if (dto.getNombreCompleto() != null)
            usuario.setNombreCompleto(dto.getNombreCompleto());

        if (dto.getPassword() != null && !dto.getPassword().isBlank())
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));

        if (dto.getCodti() != null) {
            Tienda tienda = tiendaRepository.findById(dto.getCodti())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Tienda no encontrada: " + dto.getCodti()));
            usuario.setTienda(tienda);
        }

        if (dto.getRol() != null)
            usuario.setRol(parseRol(dto.getRol()));

        if (dto.getTecnicoEncargado() != null)
            usuario.setTecnicoEncargado(dto.getTecnicoEncargado());
        if (usuario.getRol() != Rol.TECNICO)
            usuario.setTecnicoEncargado(false);   // solo un técnico puede ser el técnico encargado

        if (dto.getTelefono() != null)
            usuario.setTelefono(dto.getTelefono());

        if (dto.getEmail() != null)
            usuario.setEmail(dto.getEmail());

        if (dto.getActivo() != null)
            usuario.setActivo(dto.getActivo());

        usuario = usuarioRepository.save(usuario);

        // Actualizar perfil de empleado si existe
        Optional<EmpleadoPerfil> perfilOpt =
                empleadoPerfilRepository.findByUsuarioIdusuario(id);

        if (perfilOpt.isPresent()) {
            EmpleadoPerfil perfil = perfilOpt.get();
            if (dto.getSueldoBase()            != null) perfil.setSueldoBase(dto.getSueldoBase());
            if (dto.getFechaIngreso()           != null) perfil.setFechaIngreso(dto.getFechaIngreso());
            if (dto.getLimiteFondoInventario()  != null) perfil.setLimiteFondoInventario(dto.getLimiteFondoInventario());
            if (dto.getActivo()                 != null) perfil.setActivo(dto.getActivo());
            empleadoPerfilRepository.save(perfil);
        }

        return toDto(usuario);
    }

    // ── Desactivar (soft delete) ───────────────────────────────────────────────

    @Transactional
    public void desactivar(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado: " + id));
        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        empleadoPerfilRepository.findByUsuarioIdusuario(id)
                .ifPresent(p -> { p.setActivo(false); empleadoPerfilRepository.save(p); });
    }

    // ── Mapeo entidad → DTO ───────────────────────────────────────────────────

    private UsuarioResponseDto toDto(Usuario u) {
        UsuarioResponseDto.UsuarioResponseDtoBuilder builder = UsuarioResponseDto.builder()
                .idusuario(u.getIdusuario())
                .username(u.getUsername())
                .nombreCompleto(u.getNombreCompleto())
                .rol(u.getRol() != null ? u.getRol().name() : null)
                .telefono(u.getTelefono())
                .email(u.getEmail())
                .activo(u.getActivo())
                .tecnicoEncargado(Boolean.TRUE.equals(u.getTecnicoEncargado()))
                .fechaAlta(u.getFechaAlta())
                .codti(u.getTienda() != null ? u.getTienda().getCodti() : null)
                .nombreTienda(u.getTienda() != null ? u.getTienda().getNombre() : null);

        empleadoPerfilRepository.findByUsuarioIdusuario(u.getIdusuario())
                .ifPresent(p -> builder
                        .idempleado(p.getIdempleado())
                        .sueldoBase(p.getSueldoBase())
                        .fechaIngreso(p.getFechaIngreso())
                        .limiteFondoInventario(p.getLimiteFondoInventario())
                        .acumuladoFondoInventario(p.getAcumuladoFondoInventario()));

        return builder.build();
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private Rol parseRol(String rolStr) {
        try {
            return Rol.valueOf(rolStr.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rol inválido: " + rolStr +
                    ". Valores válidos: ROOT, ADMIN, ENCARGADO_TIENDA, VENDEDOR, TECNICO");
        }
    }
}