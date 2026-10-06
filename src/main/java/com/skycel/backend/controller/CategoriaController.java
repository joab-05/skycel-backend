package com.skycel.backend.controller;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
@Tag(name = "Categorías", description = "Catálogo maestro de categorías y subcategorías")
public class CategoriaController {

    private final CategoriaService categoriaService;

    @Operation(summary = "Listar categorías activas", description = "Con tipo, solo las de ese tipo (CELULAR, ACCESORIO, SERVICIO, TABLET).")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CategoriaResponseDTO>> listar(@RequestParam(required = false) String tipo) {
        return ResponseEntity.ok(categoriaService.listarActivas(tipo));
    }

    @Operation(summary = "Crear categoría o subcategoría")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaService.crear(request));
    }

    @Operation(summary = "Eliminar una categoría que ya no sirve",
            description = "Solo si no tiene subcategorías ni artículos activos. Es lógica: conserva el historial y se reactiva si se vuelve a crear con el mismo nombre.")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<java.util.Map<String, String>> eliminar(@PathVariable Short id) {
        categoriaService.eliminar(id);
        return ResponseEntity.ok(java.util.Map.of("mensaje", "Categoría eliminada."));
    }

    @Operation(summary = "Actualizar nombre, código corto o categoría superior")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT','ADMIN')")
    public ResponseEntity<CategoriaResponseDTO> actualizar(@PathVariable Short id,
                                                            @Valid @RequestBody CategoriaRequestDTO request) {
        return ResponseEntity.ok(categoriaService.actualizar(id, request));
    }
}
