package com.skycel.backend.service;

import com.skycel.backend.domain.dto.response.DescripcionResponseDTO;
import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.DescripcionProducto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.repository.CategoriaRepository;
import com.skycel.backend.repository.DescripcionProductoRepository;
import com.skycel.backend.repository.ProductoMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catálogo de descripciones de artículos, como un nivel más del árbol de categorías: se eligen de la lista de la categoría
 * o se crean una sola vez. Dos textos que solo difieren en mayúsculas, acentos o espacios son la misma descripción.
 */
@Service
@RequiredArgsConstructor
public class DescripcionService {

    private final DescripcionProductoRepository descripcionRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoMasterRepository productoMasterRepository;

    @Transactional(readOnly = true)
    public List<DescripcionResponseDTO> listar(Short idCategoria) {
        return descripcionRepository.findByCategoria_IdcatAndActivoTrueOrderByNombreAsc(idCategoria).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    /** Crea la descripción en la categoría, o devuelve la que ya existe con ese texto (sin duplicar). */
    @Transactional
    public DescripcionResponseDTO crear(Short idCategoria, String nombre) {
        Categoria cat = categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría no encontrada: " + idCategoria));
        return toDto(obtenerOCrear(cat, nombre));
    }

    /** La descripción de la categoría con ese texto (se reutiliza la existente, con su ortografía) o una nueva. */
    @Transactional
    public DescripcionProducto obtenerOCrear(Categoria cat, String texto) {
        String limpio = limpiar(texto);
        if (limpio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción no puede quedar vacía.");
        }
        if (cat.nivel() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las descripciones cuelgan de la subcategoría 1 o 2, no de la categoría principal.");
        }
        String clave = clave(limpio);
        for (DescripcionProducto d : descripcionRepository.findByCategoria_Idcat(cat.getIdcat())) {
            if (clave(d.getNombre()).equals(clave)) {
                if (!Boolean.TRUE.equals(d.getActivo())) {
                    d.setActivo(true);
                    descripcionRepository.save(d);
                }
                return d;
            }
        }
        return descripcionRepository.save(DescripcionProducto.builder().categoria(cat).nombre(limpio).activo(true).build());
    }

    /** La descripción con ese id, que debe ser de la misma categoría. */
    @Transactional(readOnly = true)
    public DescripcionProducto obtenerDe(Integer id, Categoria cat) {
        DescripcionProducto d = descripcionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Descripción no encontrada: " + id));
        if (!d.getCategoria().getIdcat().equals(cat.getIdcat())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La descripción '" + d.getNombre() + "' es de otra categoría ('" + d.getCategoria().getNombre() + "').");
        }
        return d;
    }

    /** Cambia el texto de la descripción: los artículos que la usan (y llevan un nombre armado) se renombran con ella. */
    @Transactional
    public DescripcionResponseDTO renombrar(Integer id, String nombre) {
        DescripcionProducto d = descripcionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Descripción no encontrada: " + id));
        String limpio = limpiar(nombre);
        if (limpio.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción no puede quedar vacía.");
        String clave = clave(limpio);
        for (DescripcionProducto otra : descripcionRepository.findByCategoria_Idcat(d.getCategoria().getIdcat())) {
            if (!otra.getIddescripcion().equals(id) && clave(otra.getNombre()).equals(clave)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe la descripción '" + otra.getNombre() + "' en esa categoría.");
            }
        }
        List<ProductoMaster> usan = productoMasterRepository.findByDescripcion_Iddescripcion(id);
        Map<Integer, Boolean> armado = new HashMap<>();
        for (ProductoMaster m : usan) armado.put(m.getIdprodmaster(), m.nombreCompleto().equalsIgnoreCase(m.getNombreBase()));
        d.setNombre(limpio);
        descripcionRepository.save(d);
        for (ProductoMaster m : usan) {
            m.setNombreProducto(limpio);
            if (Boolean.TRUE.equals(armado.get(m.getIdprodmaster()))) m.setNombreBase(m.nombreCompleto());
            productoMasterRepository.save(m);
        }
        return toDto(d);
    }

    /** Elimina (de forma lógica) una descripción que ya no sirve; solo si ningún artículo activo la usa. */
    @Transactional
    public void eliminar(Integer id) {
        DescripcionProducto d = descripcionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Descripción no encontrada: " + id));
        long usan = productoMasterRepository.countByDescripcion_IddescripcionAndActivoTrue(id);
        if (usan > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar '" + d.getNombre() + "': la usan " + usan
                    + " artículo(s) activo(s). Cámbialos de descripción antes.");
        }
        d.setActivo(false);
        descripcionRepository.save(d);
    }

    /** Desactiva las descripciones de la categoría que ya no usa ningún artículo (quedan fuera de la lista de elección). */
    @Transactional
    public int desactivarSinUso(Short idCategoria) {
        int n = 0;
        for (DescripcionProducto d : descripcionRepository.findByCategoria_IdcatAndActivoTrueOrderByNombreAsc(idCategoria)) {
            if (productoMasterRepository.findByDescripcion_Iddescripcion(d.getIddescripcion()).isEmpty()) {
                d.setActivo(false);
                descripcionRepository.save(d);
                n++;
            }
        }
        return n;
    }

    private DescripcionResponseDTO toDto(DescripcionProducto d) {
        return new DescripcionResponseDTO(d.getIddescripcion(), d.getCategoria().getIdcat(), d.getNombre());
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
    }

    /** Clave de comparación: sin acentos, en minúsculas y sin espacios de más. */
    static String clave(String texto) {
        String sinAcentos = Normalizer.normalize(limpiar(texto), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT);
    }
}
