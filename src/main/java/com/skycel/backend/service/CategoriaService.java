package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    /** Lista plana de todas las categorías activas (sin armar el árbol de subcategorías). */
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarActivas() {
        return categoriaRepository.findByActivoTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoriaResponseDTO crear(CategoriaRequestDTO dto) {
        String nombre = dto.getNombreCat().trim();
        Categoria padre = null;

        if (dto.getIdCategoriaSuperior() != null) {
            padre = categoriaRepository.findById(dto.getIdCategoriaSuperior())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Categoría superior no encontrada: " + dto.getIdCategoriaSuperior()));

            if (categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIdcat(nombre, padre.getIdcat()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una subcategoría '" + nombre + "' bajo '" + padre.getNombre() + "'.");
        } else {
            if (categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIsNull(nombre))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una categoría raíz llamada '" + nombre + "'.");
        }

        Categoria categoria = Categoria.builder()
                .nombre(nombre)
                .codigo(dto.getCodigo() != null && !dto.getCodigo().isBlank()
                        ? dto.getCodigo().trim().toUpperCase() : null)
                .categoriaSuperior(padre)
                .activo(true)
                .build();

        return toDto(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponseDTO actualizar(Short id, CategoriaRequestDTO dto) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Categoría no encontrada: " + id));

        String nombre = dto.getNombreCat().trim();
        Short idPadre = dto.getIdCategoriaSuperior();

        if (idPadre != null) {
            if (categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIdcatAndIdcatNot(nombre, idPadre, id))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe otra subcategoría '" + nombre + "' bajo esa misma categoría superior.");
            Categoria padre = categoriaRepository.findById(idPadre)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Categoría superior no encontrada: " + idPadre));
            categoria.setCategoriaSuperior(padre);
        } else {
            if (categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIsNullAndIdcatNot(nombre, id))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe otra categoría raíz llamada '" + nombre + "'.");
            categoria.setCategoriaSuperior(null);
        }

        categoria.setNombre(nombre);
        if (dto.getCodigo() != null) {
            categoria.setCodigo(dto.getCodigo().isBlank() ? null : dto.getCodigo().trim().toUpperCase());
        }

        return toDto(categoriaRepository.save(categoria));
    }

    private CategoriaResponseDTO toDto(Categoria c) {
        CategoriaResponseDTO dto = new CategoriaResponseDTO();
        dto.setIdcat(c.getIdcat());
        dto.setNombre(c.getNombre());
        dto.setCodigo(c.getCodigo());
        dto.setIdCategoriaSuperior(c.getCategoriaSuperior() != null ? c.getCategoriaSuperior().getIdcat() : null);
        return dto;
    }
}
