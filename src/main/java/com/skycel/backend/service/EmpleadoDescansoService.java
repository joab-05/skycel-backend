package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EmpleadoDescanso;
import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.repository.EmpleadoDescansoRepository;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Calendario de descansos tomados por cada empleado (bitácora de fechas, no un día fijo semanal). */
@Service
@RequiredArgsConstructor
public class EmpleadoDescansoService {

    private final EmpleadoDescansoRepository descansoRepository;
    private final EmpleadoPerfilRepository empleadoPerfilRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public Map<String, Object> registrar(Integer idempleado, LocalDate fecha, String observaciones, String username) {
        EmpleadoPerfil empleado = empleadoDe(idempleado);
        validarAcceso(usuarioPorUsername(username), empleado);
        if (fecha == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la fecha del descanso.");
        if (descansoRepository.existsByEmpleado_IdempleadoAndFecha(idempleado, fecha))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay un descanso registrado para ese empleado en esa fecha.");

        EmpleadoDescanso d = descansoRepository.save(EmpleadoDescanso.builder()
                .empleado(empleado)
                .fecha(fecha)
                .observaciones(observaciones)
                .build());
        return toMap(d);
    }

    @Transactional
    public void eliminar(Integer iddescanso, String username) {
        EmpleadoDescanso d = descansoRepository.findById(iddescanso)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Descanso no encontrado: " + iddescanso));
        validarAcceso(usuarioPorUsername(username), d.getEmpleado());
        descansoRepository.delete(d);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> porEmpleado(Integer idempleado, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now().minusMonths(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now().plusMonths(1);
        return descansoRepository.findByEmpleado_IdempleadoAndFechaBetweenOrderByFechaDesc(idempleado, d, h).stream()
                .map(this::toMap).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> porTienda(Integer codti, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now().minusMonths(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now().plusMonths(1);
        return descansoRepository.findByEmpleado_Usuario_Tienda_CodtiAndFechaBetweenOrderByFechaDesc(codti, d, h).stream()
                .map(this::toMap).collect(Collectors.toList());
    }

    private EmpleadoPerfil empleadoDe(Integer idempleado) {
        return empleadoPerfilRepository.findById(idempleado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empleado no encontrado: " + idempleado));
    }

    private Usuario usuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
    }

    /** ROOT/ADMIN operan cualquier empleado; un encargado solo los de su propia sucursal. */
    private void validarAcceso(Usuario usuario, EmpleadoPerfil empleado) {
        if (usuario.getRol() == Rol.ROOT || usuario.getRol() == Rol.ADMIN) return;
        Integer tiendaEmpleado = empleado.getUsuario().getTienda() != null ? empleado.getUsuario().getTienda().getCodti() : null;
        if (usuario.getTienda() == null || tiendaEmpleado == null || !usuario.getTienda().getCodti().equals(tiendaEmpleado))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes gestionar descansos del personal de tu sucursal.");
    }

    private Map<String, Object> toMap(EmpleadoDescanso d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("iddescanso", d.getIddescanso());
        m.put("idempleado", d.getEmpleado().getIdempleado());
        m.put("nombreEmpleado", d.getEmpleado().getUsuario().getNombreCompleto());
        m.put("fecha", d.getFecha());
        m.put("observaciones", d.getObservaciones());
        return m;
    }
}
