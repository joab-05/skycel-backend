package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.domain.util.NombreArticulo;
import com.skycel.backend.repository.CategoriaRepository;
import com.skycel.backend.repository.ProductoMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Árbol de categorías de los artículos: principal → subcategoría 1 → subcategoría 2 (opcional).
 * La principal define el comportamiento (tipo); la subcategoría 1 define el prefijo del código; el nombre
 * completo del artículo se arma con el camino (ver {@link NombreArticulo}).
 */
@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProductoMasterRepository productoMasterRepository;

    /** Lista plana de las categorías activas (sin armar el árbol de subcategorías); con tipoFiltro, solo las de ese tipo. */
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarActivas(String tipoFiltro) {
        TipoProducto tipo = tipoFiltro != null && !tipoFiltro.isBlank() ? parseTipo(tipoFiltro) : null;
        return categoriaRepository.findByActivoTrue().stream()
                .filter(c -> tipo == null || c.getTipo() == tipo)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoriaResponseDTO crear(CategoriaRequestDTO dto) {
        String nombre = dto.getNombreCat().trim();
        Categoria padre = null;
        TipoProducto tipo;

        if (dto.getIdCategoriaSuperior() != null) {
            padre = categoriaRepository.findById(dto.getIdCategoriaSuperior())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Categoría superior no encontrada: " + dto.getIdCategoriaSuperior()));
            if (!Boolean.TRUE.equals(padre.getActivo()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La categoría superior '" + padre.getNombre() + "' está desactivada.");
            if (padre.nivel() >= Categoria.NIVELES_MAX)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Las categorías llegan hasta " + Categoria.NIVELES_MAX + " niveles (principal, subcategoría 1 y subcategoría 2); '"
                                + padre.getNombre() + "' ya es el último.");
            if (categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIdcat(nombre, padre.getIdcat()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una subcategoría '" + nombre + "' bajo '" + padre.getNombre() + "'.");
            tipo = padre.getTipo();
        } else {
            if (dto.getTipo() == null || dto.getTipo().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El tipo (comportamiento) de la categoría principal es obligatorio.");
            if (categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIsNull(nombre))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una categoría raíz llamada '" + nombre + "'.");
            tipo = parseTipo(dto.getTipo());
        }

        String codigo = normalizarCodigo(dto.getCodigo());
        if (codigo != null && categoriaRepository.existsByCodigoIgnoreCase(codigo))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otra categoría con el código '" + codigo + "'.");

        Categoria categoria = Categoria.builder()
                .nombre(nombre)
                .codigo(codigo)
                .tipo(tipo)
                .categoriaSuperior(padre)
                .incluirEnNombre(dto.getIncluirEnNombre() != null ? dto.getIncluirEnNombre() : padre != null)
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

        // Subárbol afectado y sus artículos, con el nombre que tienen ahora: solo se renombran los que
        // llevan un nombre armado por el árbol (los demás tienen un nombre propio que no se toca).
        List<Short> subarbol = idsDelSubarbol(id);
        List<ProductoMaster> afectados = productoMasterRepository.findByCategoria_IdcatIn(subarbol);
        Map<Integer, Boolean> nombreArmado = new HashMap<>();
        for (ProductoMaster m : afectados) {
            nombreArmado.put(m.getIdprodmaster(), m.nombreCompleto()
                    .equalsIgnoreCase(m.getNombreBase()));
        }

        Categoria padre = null;
        if (idPadre != null) {
            if (subarbol.contains(idPadre))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una categoría no puede colgar de sí misma ni de sus subcategorías.");
            if (categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIdcatAndIdcatNot(nombre, idPadre, id))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe otra subcategoría '" + nombre + "' bajo esa misma categoría superior.");
            padre = categoriaRepository.findById(idPadre)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Categoría superior no encontrada: " + idPadre));
            if (padre.nivel() + alturaDelSubarbol(categoria) > Categoria.NIVELES_MAX)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Con esa categoría superior el árbol pasaría de " + Categoria.NIVELES_MAX + " niveles.");
        } else if (categoriaRepository.existsByNombreIgnoreCaseAndCategoriaSuperiorIsNullAndIdcatNot(nombre, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe otra categoría raíz llamada '" + nombre + "'.");
        }

        // Tipo (comportamiento): en las subcategorías es el de su categoría principal.
        TipoProducto tipoNuevo;
        if (padre != null) {
            tipoNuevo = padre.getTipo();
        } else if (dto.getTipo() != null && !dto.getTipo().isBlank()) {
            tipoNuevo = parseTipo(dto.getTipo());
        } else {
            tipoNuevo = categoria.getTipo();
        }
        if (tipoNuevo != categoria.getTipo()) {
            if (!afectados.isEmpty())
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "'" + categoria.getNombre() + "' ya tiene artículos registrados: no se puede cambiar su tipo (comportamiento).");
            categoria.setTipo(tipoNuevo);
            for (Short idHija : subarbol) {
                if (!idHija.equals(id)) categoriaRepository.findById(idHija).ifPresent(h -> h.setTipo(tipoNuevo));
            }
        }

        if (dto.getCodigo() != null) {
            String codigo = normalizarCodigo(dto.getCodigo());
            if (codigo != null && categoriaRepository.existsByCodigoIgnoreCaseAndIdcatNot(codigo, id))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otra categoría con el código '" + codigo + "'.");
            categoria.setCodigo(codigo);
        }
        categoria.setCategoriaSuperior(padre);
        categoria.setNombre(nombre);
        if (dto.getIncluirEnNombre() != null) categoria.setIncluirEnNombre(dto.getIncluirEnNombre());

        Categoria guardada = categoriaRepository.save(categoria);

        for (ProductoMaster m : afectados) {
            if (Boolean.TRUE.equals(nombreArmado.get(m.getIdprodmaster()))) {
                m.setNombreBase(m.nombreCompleto());
                productoMasterRepository.save(m);
            }
        }
        return toDto(guardada);
    }

    /** Ids de la categoría y de todas sus descendientes. */
    private List<Short> idsDelSubarbol(Short raiz) {
        List<Short> ids = new ArrayList<>();
        List<Short> pendientes = new ArrayList<>(List.of(raiz));
        while (!pendientes.isEmpty() && ids.size() < 500) {
            Short actual = pendientes.remove(0);
            if (ids.contains(actual)) continue;
            ids.add(actual);
            pendientes.addAll(categoriaRepository.findIdsByCategoriaSuperiorId(actual));
        }
        return ids;
    }

    /** Niveles que ocupa la categoría con sus descendientes (1 = sola, 2 = con hijas...). */
    private int alturaDelSubarbol(Categoria c) {
        int max = 0;
        for (Short idHija : categoriaRepository.findIdsByCategoriaSuperiorId(c.getIdcat())) {
            Categoria h = categoriaRepository.findById(idHija).orElse(null);
            if (h != null) max = Math.max(max, alturaDelSubarbol(h));
        }
        return 1 + max;
    }

    private String normalizarCodigo(String codigo) {
        return codigo == null || codigo.isBlank() ? null : codigo.trim().toUpperCase();
    }

    private TipoProducto parseTipo(String tipoStr) {
        try {
            return TipoProducto.valueOf(tipoStr.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo inválido: " + tipoStr + ". Valores válidos: CELULAR, ACCESORIO, SERVICIO, TABLET");
        }
    }

    private CategoriaResponseDTO toDto(Categoria c) {
        CategoriaResponseDTO dto = new CategoriaResponseDTO();
        dto.setIdcat(c.getIdcat());
        dto.setNombre(c.getNombre());
        dto.setCodigo(c.getCodigo());
        dto.setTipo(c.getTipo() != null ? c.getTipo().name() : null);
        dto.setIdCategoriaSuperior(c.getCategoriaSuperior() != null ? c.getCategoriaSuperior().getIdcat() : null);
        dto.setIncluirEnNombre(c.incluyeEnNombre());
        dto.setNivel(c.nivel());
        return dto;
    }
}
