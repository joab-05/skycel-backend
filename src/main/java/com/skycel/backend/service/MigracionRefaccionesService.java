package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.CategoriaFolio;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.producto.MigracionRefaccionesDto;
import com.skycel.backend.repository.CategoriaFolioRepository;
import com.skycel.backend.repository.CategoriaRepository;
import com.skycel.backend.repository.ProductoMasterRepository;
import com.skycel.backend.repository.ProductoRepository;
import com.skycel.backend.repository.TiendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pasa a «Refacción» los productos de tipo accesorio que son del taller (la tienda marcada como taller). Antes las refacciones se
 * registraban como accesorios; ahora tienen su propio tipo, su propia categoría principal («Refacciones») y no se venden en el punto de
 * venta.
 *
 * <p>Reglas: solo se cambian los artículos que existen <b>únicamente</b> en el taller (uno que también está en otra tienda no se toca y se
 * reporta); los servicios no cambian. Cada artículo se pasa a la categoría equivalente bajo la nueva principal «Refacciones» (la subcategoría
 * «Refacciones» de Accesorios pasa a llamarse «General»): se conservan los niveles, las descripciones y el consecutivo de los códigos
 * (el código corto pasa a la categoría nueva si la de antes queda vacía, que entonces se retira; si no, la nueva lleva uno parecido). Se puede
 * repetir sin duplicar nada.
 */
@Service
@RequiredArgsConstructor
public class MigracionRefaccionesService {

    public static final String RAIZ = "Refacciones";
    public static final String GENERAL = "General";
    private static final String CODIGO_RAIZ = "REF";
    private static final int EJEMPLOS = 12;

    private final TiendaRepository tiendaRepository;
    private final ProductoRepository productoRepository;
    private final ProductoMasterRepository masterRepository;
    private final CategoriaRepository categoriaRepository;
    private final CategoriaFolioRepository folioRepository;
    private final DescripcionService descripcionService;

    /** ¿Ya existe la categoría principal «Refacciones» de tipo Refacción? Es la marca de que la migración ya se hizo. */
    @Transactional(readOnly = true)
    public boolean yaSeHizo() {
        return categoriaRepository.findByNombreIgnoreCaseAndCategoriaSuperiorIsNull(RAIZ)
                .filter(c -> c.getTipo() == TipoProducto.REFACCION).isPresent();
    }

    /** Lo que haría, sin guardar nada. */
    @Transactional(readOnly = true)
    public MigracionRefaccionesDto planear() {
        return ejecutar(false);
    }

    @Transactional
    public MigracionRefaccionesDto aplicar() {
        return ejecutar(true);
    }

    private MigracionRefaccionesDto ejecutar(boolean aplicar) {
        List<Tienda> talleres = tiendaRepository.findByEsTallerTrue();
        if (talleres.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay una tienda marcada como taller.");
        Set<Integer> codtis = talleres.stream().map(Tienda::getCodti).collect(Collectors.toSet());

        // Los artículos de tipo accesorio del taller, con sus productos
        Map<Integer, ProductoMaster> masters = new LinkedHashMap<>();
        Map<Integer, Integer> productosPorMaster = new HashMap<>();
        Map<Integer, Integer> conExistenciaPorMaster = new HashMap<>();
        Set<Integer> yaMigrados = new HashSet<>();
        for (Tienda t : talleres) {
            for (Producto p : productoRepository.findByTienda_Codti(t.getCodti())) {
                ProductoMaster m = p.getProductoMaster();
                if (m == null) continue;
                if (m.getTipo() == TipoProducto.REFACCION) {
                    yaMigrados.add(m.getIdprodmaster());
                    continue;
                }
                if (m.getTipo() != TipoProducto.ACCESORIO) continue;
                masters.putIfAbsent(m.getIdprodmaster(), m);
                productosPorMaster.merge(m.getIdprodmaster(), 1, Integer::sum);
                if (p.getStock() != null && p.getStock().compareTo(BigDecimal.ZERO) > 0) conExistenciaPorMaster.merge(m.getIdprodmaster(), 1, Integer::sum);
            }
        }

        List<ProductoMaster> aMigrar = new ArrayList<>();
        List<String> omitidos = new ArrayList<>();
        for (ProductoMaster m : masters.values()) {
            if (productoRepository.existsByProductoMaster_IdprodmasterAndTienda_CodtiNotIn(m.getIdprodmaster(), codtis)) omitidos.add(m.getNombreBase());
            else aMigrar.add(m);
        }
        int productos = aMigrar.stream().mapToInt(m -> productosPorMaster.getOrDefault(m.getIdprodmaster(), 0)).sum();
        int conExistencia = aMigrar.stream().mapToInt(m -> conExistenciaPorMaster.getOrDefault(m.getIdprodmaster(), 0)).sum();

        Categoria raiz = categoriaRepository.findByNombreIgnoreCaseAndCategoriaSuperiorIsNull(RAIZ).orElse(null);
        if (raiz != null && raiz.getTipo() != TipoProducto.REFACCION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría principal «" + RAIZ + "» que no es de tipo Refacción; renómbrala antes de migrar.");
        }
        boolean raizNueva = raiz == null && !aMigrar.isEmpty();
        if (raizNueva && aplicar) {
            raiz = categoriaRepository.save(Categoria.builder().nombre(RAIZ).tipo(TipoProducto.REFACCION).activo(true).incluirEnNombre(false).build());
        }

        Map<String, Categoria> creadas = new HashMap<>();                  // camino → categoría (al aplicar)
        Set<String> nuevas = new LinkedHashSet<>();                        // caminos que no existían
        List<Categoria[]> pares = new ArrayList<>();                       // [la de antes, la nueva]
        Map<Short, Categoria> deAntes = new LinkedHashMap<>();             // las categorías de Accesorios que se dejaron
        List<String> ejemplos = new ArrayList<>();
        for (ProductoMaster m : aMigrar) {
            List<Categoria> ruta = m.getCategoria() == null ? List.of() : m.getCategoria().ruta();
            List<Categoria> originales = ruta.size() > 1 ? new ArrayList<>(ruta.subList(1, ruta.size())) : new ArrayList<>();
            List<String> nombres = new ArrayList<>();
            for (Categoria c : originales) nombres.add(c.getNombre());
            if (nombres.isEmpty()) nombres.add(GENERAL);                                  // estaba en la principal: va a «General»
            else if (nombres.get(0).equalsIgnoreCase(RAIZ)) nombres.set(0, GENERAL);      // la antigua subcategoría «Refacciones»
            nombres = nombres.subList(0, Math.min(nombres.size(), Categoria.NIVELES_MAX - 1));

            Categoria padre = raiz;
            String camino = RAIZ;
            for (int i = 0; i < nombres.size(); i++) {
                camino = camino + " > " + nombres.get(i);
                Categoria original = i < originales.size() ? originales.get(i) : null;
                Categoria existente = creadas.get(camino);
                if (existente == null && padre != null) {
                    existente = categoriaRepository.findByNombreIgnoreCaseAndCategoriaSuperiorIdcat(nombres.get(i), padre.getIdcat()).orElse(null);
                }
                if (existente == null) {
                    nuevas.add(camino);
                    if (aplicar) {
                        existente = crearNodo(nombres.get(i), padre, original);
                        pares.add(new Categoria[]{original, existente});
                    }
                }
                if (existente != null) creadas.put(camino, existente);
                if (original != null && original.getIdcat() != null) deAntes.putIfAbsent(original.getIdcat(), original);
                padre = existente;
            }

            if (ejemplos.size() < EJEMPLOS) {
                ejemplos.add(m.getNombreBase() + ":  " + (ruta.isEmpty() ? "(sin categoría)" : ruta.stream().map(Categoria::getNombre).collect(Collectors.joining(" > ")))
                        + "  →  " + camino);
            }
            if (aplicar) {
                m.setTipo(TipoProducto.REFACCION);
                m.setCategoria(padre);
                if (m.getDescripcion() != null) m.setDescripcion(descripcionService.obtenerOCrear(padre, m.getDescripcion().getNombre()));
                masterRepository.save(m);
            }
        }

        List<String> retiradas = new ArrayList<>();
        if (aplicar) {
            masterRepository.flush();
            retirarVacias(deAntes.values(), retiradas);
            asignarCodigos(pares, raiz);
        }
        return new MigracionRefaccionesDto(aplicar, talleres.stream().map(Tienda::getNombre).toList(), aMigrar.size(), productos, conExistencia,
                yaMigrados.size(), raizNueva, new ArrayList<>(nuevas), retiradas, omitidos, ejemplos);
    }

    /** Una categoría nueva bajo «Refacciones», con el consecutivo de códigos de la que reemplaza. El código corto se asigna al final. */
    private Categoria crearNodo(String nombre, Categoria padre, Categoria original) {
        Categoria nueva = categoriaRepository.save(Categoria.builder().nombre(nombre).tipo(TipoProducto.REFACCION).categoriaSuperior(padre)
                .activo(true).incluirEnNombre(original != null ? original.getIncluirEnNombre() : Boolean.TRUE).build());
        if (original != null && original.getIdcat() != null) {
            folioRepository.findById(original.getIdcat()).map(CategoriaFolio::getUltimoFolio).filter(f -> f != null && f > 0)
                    .ifPresent(f -> folioRepository.fijarMinimo(nueva.getIdcat(), f));
        }
        return nueva;
    }

    /** Las categorías de antes que ya no tienen artículos (ni ellas ni las de abajo) se retiran, para que no queden vacías en Accesorios. */
    private void retirarVacias(Collection<Categoria> deAntes, List<String> retiradas) {
        List<Categoria> ordenadas = new ArrayList<>(deAntes);
        ordenadas.sort(Comparator.comparingInt(Categoria::nivel).reversed());
        for (Categoria c : ordenadas) {
            if (Boolean.TRUE.equals(c.getActivo()) && sinArticulos(c)) {
                c.setActivo(false);
                categoriaRepository.save(c);
                retiradas.add(c.ruta().stream().map(Categoria::getNombre).collect(Collectors.joining(" > ")));
            }
        }
        categoriaRepository.flush();
    }

    private boolean sinArticulos(Categoria c) {
        if (masterRepository.countByCategoria_Idcat(c.getIdcat()) > 0) return false;
        for (Short hija : categoriaRepository.findIdsByCategoriaSuperiorId(c.getIdcat())) {
            Categoria h = categoriaRepository.findById(hija).orElse(null);
            if (h != null && Boolean.TRUE.equals(h.getActivo()) && !sinArticulos(h)) return false;
        }
        return true;
    }

    /**
     * El código corto de la categoría de antes pasa a la nueva si aquella se retiró (los códigos son únicos en todo el sistema); si sigue
     * en uso, la nueva lleva uno parecido con una R delante.
     */
    private void asignarCodigos(List<Categoria[]> pares, Categoria raiz) {
        Set<Short> hechas = new HashSet<>();
        for (Categoria[] par : pares) {
            Categoria antes = par[0], nueva = par[1];
            if (antes == null || antes.getCodigo() == null || antes.getCodigo().isBlank() || !hechas.add(nueva.getIdcat())) continue;
            String codigo = antes.getCodigo().trim().toUpperCase();
            if (!Boolean.TRUE.equals(antes.getActivo())) {
                antes.setCodigo(null);
                categoriaRepository.saveAndFlush(antes);
                nueva.setCodigo(codigo);
            } else {
                nueva.setCodigo(codigoLibre("R" + codigo));
            }
            categoriaRepository.save(nueva);
        }
        // «General» sin una categoría de antes que le dé su código, y la principal
        for (Categoria c : new ArrayList<>(pares.stream().map(p -> p[1]).toList())) {
            if (GENERAL.equalsIgnoreCase(c.getNombre()) && (c.getCodigo() == null || c.getCodigo().isBlank())) {
                c.setCodigo(codigoLibre(CODIGO_RAIZ));
                categoriaRepository.save(c);
            }
        }
        if (raiz != null && (raiz.getCodigo() == null || raiz.getCodigo().isBlank()) && !categoriaRepository.existsByCodigoIgnoreCase(CODIGO_RAIZ)) {
            raiz.setCodigo(CODIGO_RAIZ);
            categoriaRepository.save(raiz);
        }
    }

    private String codigoLibre(String base) {
        String b = base.length() > 10 ? base.substring(0, 10) : base;
        String c = b;
        for (int i = 2; categoriaRepository.existsByCodigoIgnoreCase(c) && i < 100; i++) {
            String sufijo = String.valueOf(i);
            c = (b.length() + sufijo.length() > 10 ? b.substring(0, 10 - sufijo.length()) : b) + sufijo;
        }
        return c;
    }
}
