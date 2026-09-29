package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EmpleadoPerfil;
import com.skycel.backend.domain.entity.EmpleadoPrestamo;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.repository.EmpleadoPerfilRepository;
import com.skycel.backend.repository.EmpleadoPrestamoRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Préstamos/adelantos a empleados. Solo uno ACTIVO a la vez por empleado, para que no haya ambigüedad sobre
 * a cuál abonar desde la nómina — si hay una excepción real (varios préstamos vigentes), primero se salda o
 * se ajusta el que ya existe.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoPrestamoService {

    public static final byte ACTIVO = 0;
    public static final byte LIQUIDADO = 1;

    private final EmpleadoPrestamoRepository prestamoRepository;
    private final EmpleadoPerfilRepository empleadoPerfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoCajaService movimientoCajaService;

    @Transactional
    public Map<String, Object> otorgar(Integer idempleado, BigDecimal monto, Integer idCaja, String observaciones, String username) {
        if (monto == null || monto.signum() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto del préstamo debe ser mayor a cero.");
        EmpleadoPerfil empleado = empleadoDe(idempleado);
        if (prestamoRepository.findByEmpleado_IdempleadoAndEstado(idempleado, ACTIVO).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este empleado ya tiene un préstamo activo; salde o ajuste ese antes de otorgar otro.");

        Usuario usuario = usuarioPorUsername(username);
        EmpleadoPrestamo prestamo = prestamoRepository.save(EmpleadoPrestamo.builder()
                .empleado(empleado)
                .montoOriginal(monto)
                .saldoPendiente(monto)
                .fecha(LocalDate.now())
                .observaciones(observaciones)
                .estado(ACTIVO)
                .build());
        movimientoCajaService.registrarPrestamoEmpleado(idCaja, prestamo, usuario);
        return toMap(prestamo);
    }

    /** Reduce el saldo pendiente por un abono (normalmente hecho desde la nómina); liquida si llega a cero. */
    @Transactional
    public void abonar(Integer idprestamo, BigDecimal monto) {
        EmpleadoPrestamo prestamo = prestamoRepository.findById(idprestamo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Préstamo no encontrado: " + idprestamo));
        BigDecimal nuevoSaldo = prestamo.getSaldoPendiente().subtract(monto);
        if (nuevoSaldo.signum() < 0) nuevoSaldo = BigDecimal.ZERO;
        prestamo.setSaldoPendiente(nuevoSaldo);
        if (nuevoSaldo.signum() == 0) prestamo.setEstado(LIQUIDADO);
        prestamoRepository.save(prestamo);
    }

    @Transactional(readOnly = true)
    public Optional<EmpleadoPrestamo> activoDe(Integer idempleado) {
        return prestamoRepository.findByEmpleado_IdempleadoAndEstado(idempleado, ACTIVO);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarPorEmpleado(Integer idempleado) {
        return prestamoRepository.findByEmpleado_IdempleadoOrderByFechaDesc(idempleado).stream()
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

    private Map<String, Object> toMap(EmpleadoPrestamo p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idprestamo", p.getIdprestamo());
        m.put("idempleado", p.getEmpleado().getIdempleado());
        m.put("nombreEmpleado", p.getEmpleado().getUsuario().getNombreCompleto());
        m.put("montoOriginal", p.getMontoOriginal());
        m.put("saldoPendiente", p.getSaldoPendiente());
        m.put("fecha", p.getFecha());
        m.put("observaciones", p.getObservaciones());
        m.put("estado", p.getEstado());
        m.put("estadoDisplay", p.getEstado() == ACTIVO ? "Activo" : "Liquidado");
        return m;
    }
}
