package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Cliente;
import com.skycel.backend.dto.cliente.ClienteRequestDto;
import com.skycel.backend.dto.cliente.ClienteResponseDto;
import com.skycel.backend.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    // ── Listar ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ClienteResponseDto> listarTodos() {
        return clienteRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClienteResponseDto> listarActivos() {
        return clienteRepository.findByActivoTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClienteResponseDto obtenerPorId(Integer id) {
        return toDto(buscarOFallar(id));
    }

    /** Usado por el POS para venta de mostrador: busca por teléfono exacto. */
    @Transactional(readOnly = true)
    public java.util.Optional<ClienteResponseDto> buscarPorTelefono(String telefono) {
        return clienteRepository.findByTelefono(telefono).map(this::toDto);
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Transactional
    public ClienteResponseDto crear(ClienteRequestDto dto) {
        if (dto.getNombreCompleto() == null || dto.getNombreCompleto().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre completo es obligatorio.");

        Cliente cliente = Cliente.builder()
                .nombreCompleto(dto.getNombreCompleto().trim())
                .telefono(dto.getTelefono())
                .correo(dto.getCorreo())
                .direccion(dto.getDireccion())
                .tipoCliente(dto.getTipoCliente() != null ? dto.getTipoCliente() : 1)
                .margenFactor(dto.getMargenFactor())
                .puntos(dto.getPuntos() != null ? dto.getPuntos() : 0)
                .activo(true)
                .build();

        return toDto(clienteRepository.save(cliente));
    }

    // ── Actualizar ────────────────────────────────────────────────────────────

    @Transactional
    public ClienteResponseDto actualizar(Integer id, ClienteRequestDto dto) {
        Cliente cliente = buscarOFallar(id);

        if (dto.getNombreCompleto() != null && !dto.getNombreCompleto().isBlank())
            cliente.setNombreCompleto(dto.getNombreCompleto().trim());
        if (dto.getTelefono()    != null) cliente.setTelefono(dto.getTelefono());
        if (dto.getCorreo()      != null) cliente.setCorreo(dto.getCorreo());
        if (dto.getDireccion()   != null) cliente.setDireccion(dto.getDireccion());
        if (dto.getTipoCliente() != null) cliente.setTipoCliente(dto.getTipoCliente());
        if (dto.getMargenFactor()!= null) cliente.setMargenFactor(dto.getMargenFactor());
        if (dto.getPuntos()      != null) cliente.setPuntos(dto.getPuntos());

        return toDto(clienteRepository.save(cliente));
    }

    // ── Desactivar ────────────────────────────────────────────────────────────

    @Transactional
    public void desactivar(Integer id) {
        Cliente c = buscarOFallar(id);
        c.setActivo(false);
        clienteRepository.save(c);
    }

    // ── Sumar puntos ──────────────────────────────────────────────────────────

    @Transactional
    public ClienteResponseDto sumarPuntos(Integer id, int puntos) {
        Cliente c = buscarOFallar(id);
        c.setPuntos((c.getPuntos() != null ? c.getPuntos() : 0) + puntos);
        return toDto(clienteRepository.save(c));
    }

    // ── Mapeo ─────────────────────────────────────────────────────────────────

    private ClienteResponseDto toDto(Cliente c) {
        Long       compras  = clienteRepository.contarVentasPorCliente(c.getIdcliente());
        BigDecimal gastado  = clienteRepository.totalGastadoPorCliente(c.getIdcliente());

        byte tipo = c.getTipoCliente() != null ? c.getTipoCliente() : 1;
        return ClienteResponseDto.builder()
                .idcliente(c.getIdcliente())
                .nombreCompleto(c.getNombreCompleto())
                .telefono(c.getTelefono())
                .correo(c.getCorreo())
                .direccion(c.getDireccion())
                .tipoCliente(c.getTipoCliente())
                .tipoClienteDisplay(tipoDisplay(tipo))
                .tipoColor(tipoColor(tipo))
                .margenFactor(c.getMargenFactor())
                .puntos(c.getPuntos() != null ? c.getPuntos() : 0)
                .totalCompras(compras)
                .totalGastado(gastado)
                .fechaRegistro(c.getFechaRegistro())
                .activo(c.getActivo())
                .build();
    }

    private String tipoDisplay(byte tipo) {
        return switch (tipo) {
            case 2  -> "Frecuente";
            case 3  -> "VIP";
            default -> "Regular";
        };
    }

    private String tipoColor(byte tipo) {
        return switch (tipo) {
            case 2  -> "#f59e0b";  // amarillo frecuente
            case 3  -> "#8b5cf6";  // morado VIP
            default -> "#64748b";  // gris regular
        };
    }

    private Cliente buscarOFallar(Integer id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado: " + id));
    }
}