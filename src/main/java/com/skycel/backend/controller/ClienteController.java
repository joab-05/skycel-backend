package com.skycel.backend.controller;

import com.skycel.backend.dto.cliente.ClienteRequestDto;
import com.skycel.backend.dto.cliente.ClienteResponseDto;
import com.skycel.backend.service.ClienteService;
import com.skycel.backend.shared.dto.StandardApiResponse;
import com.skycel.backend.shared.response.ResponseEntityBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<List<ClienteResponseDto>>> listar(
            @RequestParam(defaultValue = "false") boolean soloActivos) {
        var lista = soloActivos ? clienteService.listarActivos() : clienteService.listarTodos();
        return ResponseEntityBuilder.ok(lista, "clientes");
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<ClienteResponseDto>> obtener(@PathVariable Integer id) {
        return ResponseEntityBuilder.ok(clienteService.obtenerPorId(id), "cliente");
    }

    /** Usado por el POS para venta de mostrador: busca por teléfono exacto. 404 si no existe. */
    @GetMapping("/telefono/{telefono}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<ClienteResponseDto>> buscarPorTelefono(@PathVariable String telefono) {
        var cliente = clienteService.buscarPorTelefono(telefono)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "No existe cliente con ese teléfono."));
        return ResponseEntityBuilder.ok(cliente, "cliente");
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<ClienteResponseDto>> crear(@RequestBody ClienteRequestDto dto) {
        return ResponseEntityBuilder.created(clienteService.crear(dto), "cliente");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN','ENCARGADO_TIENDA')")
    public ResponseEntity<StandardApiResponse<ClienteResponseDto>> actualizar(
            @PathVariable Integer id, @RequestBody ClienteRequestDto dto) {
        return ResponseEntityBuilder.updated(clienteService.actualizar(id, dto), "cliente");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<StandardApiResponse<Void>> desactivar(@PathVariable Integer id) {
        clienteService.desactivar(id);
        return ResponseEntityBuilder.deleted("cliente");
    }

    @PatchMapping("/{id}/puntos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StandardApiResponse<ClienteResponseDto>> sumarPuntos(
            @PathVariable Integer id, @RequestParam int cantidad) {
        return ResponseEntityBuilder.updated(clienteService.sumarPuntos(id, cantidad), "cliente");
    }
}