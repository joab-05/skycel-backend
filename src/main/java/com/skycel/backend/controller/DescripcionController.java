package com.skycel.backend.controller;

import com.skycel.backend.domain.dto.request.DescripcionRequestDTO;
import com.skycel.backend.domain.dto.response.DescripcionResponseDTO;
import com.skycel.backend.service.DescripcionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/descripciones")
@RequiredArgsConstructor
@Tag(name = "Descripciones", description = "Catálogo de descripciones de artículos, colgadas de la categoría (como un nivel más del árbol)")
public class DescripcionController {

    private final DescripcionService descripcionService;

    @Operation(summary = "Descripciones de una categoría")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DescripcionResponseDTO>> listar(@RequestParam Short idCategoria) {
        return ResponseEntity.ok(descripcionService.listar(idCategoria));
    }

    @Operation(summary = "Crear una descripción", description = "Si ya existe una igual en la categoría (sin importar mayúsculas, acentos o espacios) devuelve esa.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<DescripcionResponseDTO> crear(@Valid @RequestBody DescripcionRequestDTO request) {
        if (request.getIdCategoria() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría es obligatoria.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(descripcionService.crear(request.getIdCategoria(), request.getNombre()));
    }

    @Operation(summary = "Eliminar una descripción que ya no sirve", description = "Solo si ningún artículo activo la usa. Es lógica.")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<java.util.Map<String, String>> eliminar(@PathVariable Integer id) {
        descripcionService.eliminar(id);
        return ResponseEntity.ok(java.util.Map.of("mensaje", "Descripción eliminada."));
    }

    @Operation(summary = "Cambiar el texto de una descripción", description = "Renombra también los artículos que la usan.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<DescripcionResponseDTO> renombrar(@PathVariable Integer id, @Valid @RequestBody DescripcionRequestDTO request) {
        return ResponseEntity.ok(descripcionService.renombrar(id, request.getNombre()));
    }
}
