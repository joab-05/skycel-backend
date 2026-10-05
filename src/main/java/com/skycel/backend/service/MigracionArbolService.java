package com.skycel.backend.service;

import com.skycel.backend.domain.dto.response.MigracionArbolResultadoDTO;
import com.skycel.backend.domain.dto.response.MigracionArbolResultadoDTO.Linea;
import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.DescuentoRegla;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.domain.util.NombreArticulo;
import com.skycel.backend.repository.CategoriaFolioRepository;
import com.skycel.backend.repository.CategoriaRepository;
import com.skycel.backend.repository.DescuentoReglaRepository;
import com.skycel.backend.repository.ProductoMasterRepository;
import com.skycel.backend.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.*;

/**
 * Convierte el catálogo anterior (categoría + marca/modelo/descripción + color por producto) al árbol de categorías:
 * principal (Equipos / Accesorios / Servicios) → subcategoría 1 → subcategoría 2 → artículo, con el color como parte
 * del nombre del artículo.
 *
 * {@link #planear()} corre exactamente el mismo código que {@link #aplicar()} y luego deshace la transacción, así que
 * el reporte muestra lo que de verdad pasaría. Se puede repetir: los artículos que ya cuelgan de una categoría
 * principal del árbol (incluirEnNombre = false) se saltan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigracionArbolService {

    /** Contador reservado de los equipos antiguos (CEL-000001...), ver ProductoService. */
    private static final short FOLIO_EQUIPOS = 0;

    private enum Grupo { EQUIPOS, ACCESORIOS, SERVICIOS }

    private final CategoriaRepository categoriaRepository;
    private final ProductoMasterRepository productoMasterRepository;
    private final ProductoRepository productoRepository;
    private final CategoriaFolioRepository categoriaFolioRepository;
    private final DescuentoReglaRepository descuentoReglaRepository;

    @Transactional
    public MigracionArbolResultadoDTO planear() {
        MigracionArbolResultadoDTO r = new Corrida().ejecutar();
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        r.setAplicado(false);
        return r;
    }

    @Transactional
    public MigracionArbolResultadoDTO aplicar() {
        MigracionArbolResultadoDTO r = new Corrida().ejecutar();
        r.setAplicado(true);
        return r;
    }

    /** Una pasada de migración: guarda el estado (categorías conocidas, principales resueltas) mientras recorre los artículos. */
    private class Corrida {
        private final MigracionArbolResultadoDTO resultado = new MigracionArbolResultadoDTO();
        private final List<Categoria> todas = new ArrayList<>(categoriaRepository.findAll());
        private final Map<Grupo, Categoria> principales = new EnumMap<>(Grupo.class);
        private final Map<Short, String> codigoAnteriorDeRaiz = new HashMap<>();
        private final Set<String> nombresAsignados = new HashSet<>();
        /** Decisión por nombre de subcategoría 1 de accesorios: ¿entra en el nombre de sus artículos? */
        private final Map<String, Boolean> incluirSub1 = new HashMap<>();

        MigracionArbolResultadoDTO ejecutar() {
            List<ProductoMaster> masters = new ArrayList<>(productoMasterRepository.findAll());
            masters.sort(Comparator.comparing(ProductoMaster::getIdprodmaster));
            List<DescuentoRegla> reglas = descuentoReglaRepository.findByActivoTrue();
            productoMasterRepository.findByActivoTrue().forEach(m -> nombresAsignados.add(clave(m.getNombreBase())));

            // Se decide antes de tocar nada: al convertir una raíz anterior en principal esta pasa a marcarse como del árbol.
            Set<Integer> yaMigrados = new HashSet<>();
            for (ProductoMaster m : masters) {
                if (m.getCategoria() != null && Boolean.FALSE.equals(m.getCategoria().raiz().getIncluirEnNombre())) {
                    yaMigrados.add(m.getIdprodmaster());
                }
            }
            resultado.setYaMigrados(yaMigrados.size());
            decidirInclusionDeSub1Accesorios(masters, yaMigrados);
            for (ProductoMaster m : masters) {
                if (!yaMigrados.contains(m.getIdprodmaster())) migrar(m, reglas);
            }
            desactivarCategoriasAntiguasVacias(masters);
            return resultado;
        }

        /**
         * Los nombres de los accesorios ya están curados. Una subcategoría 1 entra en el nombre solo si la mayoría de sus
         * artículos ya empiezan con ella ("Funda Silicón..." en "Funda"); si no, quedaría repetida ("Fundas Funda Silicón...").
         */
        private void decidirInclusionDeSub1Accesorios(List<ProductoMaster> masters, Set<Integer> yaMigrados) {
            Categoria candidata = candidata(Grupo.ACCESORIOS);
            Map<String, int[]> conteo = new HashMap<>(); // nombre → {empiezan con el nombre, total}
            for (ProductoMaster m : masters) {
                if (yaMigrados.contains(m.getIdprodmaster()) || grupoDe(m.getTipo()) != Grupo.ACCESORIOS || m.getCategoria() == null) continue;
                String sub1 = nombreSub1Anterior(m, candidata);
                int[] c = conteo.computeIfAbsent(clave(sub1), k -> new int[2]);
                c[1]++;
                if (restoTrasPrefijo(m.getNombreBase(), sub1) != null) c[0]++;
            }
            conteo.forEach((k, c) -> incluirSub1.put(k, c[0] * 2 >= c[1]));
        }

        /** Nombre de la subcategoría 1 que tendrá un accesorio según su categoría anterior (ver nivelesAntiguos). */
        private String nombreSub1Anterior(ProductoMaster m, Categoria candidata) {
            List<Categoria> ruta = m.getCategoria().ruta();
            if (candidata != null && ruta.get(0).getIdcat().equals(candidata.getIdcat())) {
                return ruta.size() >= 2 ? ruta.get(1).getNombre() : "General";
            }
            return ruta.get(0).getNombre();
        }

        // ── Un artículo ───────────────────────────────────────────────────────

        private void migrar(ProductoMaster m, List<DescuentoRegla> reglas) {
            Grupo grupo = grupoDe(m.getTipo());
            Categoria principal = principal(grupo);
            String marca = limpiar(m.getMarca());
            String modelo = limpiar(m.getModelo());
            String especificaciones = limpiar(m.getEspecificaciones());
            String nota = limpiar(m.getNotaAdicional());
            String nombreAnterior = m.getNombreBase();

            Categoria sub1;
            Categoria sub2 = null;
            String hoja;
            switch (grupo) {
                case EQUIPOS -> {
                    boolean tablet = m.getTipo() == TipoProducto.TABLET;
                    sub1 = hijo(principal, tablet ? "Tablet" : "Celular", tablet ? "TAB" : "CEL", true);
                    if (marca != null) sub2 = hijo(sub1, marca, null, true);
                    hoja = modelo != null ? modelo : sinPrefijo(nombreAnterior, marca);
                }
                case ACCESORIOS -> {
                    Categoria[] niveles = nivelesAntiguos(m, principal);
                    sub1 = niveles[0];
                    sub2 = niveles[1];
                    hoja = nombreAnterior; // nombre ya curado: es la variante completa salvo que empiece con el camino
                }
                default -> {
                    Categoria[] niveles = nivelesAntiguos(m, principal);
                    sub1 = niveles[0];
                    sub1.setIncluirEnNombre(false); // "Reparación", "Software": el nombre del servicio empieza por el trabajo
                    categoriaRepository.save(sub1);
                    if (especificaciones != null) {
                        sub2 = hijo(sub1, especificaciones, null, true);
                        hoja = nota;
                    } else {
                        sub2 = niveles[1];
                        hoja = nota != null ? nota : sinPrefijo(nombreAnterior, sub2 != null ? sub2.getNombre() : null);
                    }
                }
            }
            Categoria destino = sub2 != null ? sub2 : sub1;
            // Los nombres ya curados se respetan: si el nombre actual empieza con el camino de categorías,
            // lo que sigue es la variante y el nombre completo no cambia (salvo por el color que se agrega).
            String resto = restoTrasPrefijo(nombreAnterior, NombreArticulo.construir(destino, null));
            if (resto != null) hoja = resto.isEmpty() ? null : resto;
            TipoProducto tipoNuevo = grupo == Grupo.EQUIPOS ? TipoProducto.CELULAR : m.getTipo();
            if (m.getTipo() == TipoProducto.TABLET) resultado.setTabletsComoEquipo(resultado.getTabletsComoEquipo() + 1);

            // Un artículo por cada color: el color pasa a ser parte del nombre.
            List<Producto> productos = productoRepository.findByProductoMaster_IdprodmasterOrderByIdproductoAsc(m.getIdprodmaster());
            Map<Short, List<Producto>> porColor = new LinkedHashMap<>();
            for (Producto p : productos) {
                porColor.computeIfAbsent(p.getColor() != null ? p.getColor().getIdcolor() : null, k -> new ArrayList<>()).add(p);
            }

            if (porColor.size() <= 1) {
                String color = productos.isEmpty() || productos.get(0).getColor() == null ? null : productos.get(0).getColor().getNombre();
                String nombreProducto = unir(hoja, color);
                m.setCategoria(destino);
                m.setTipo(tipoNuevo);
                m.setNombreProducto(nombreProducto);
                m.setNombreBase(NombreArticulo.construir(destino, nombreProducto));
                productoMasterRepository.save(m);
                registrar(m, nombreAnterior, "ACTUALIZADO", productos.size(), destino);
            } else {
                for (Map.Entry<Short, List<Producto>> grupoColor : porColor.entrySet()) {
                    List<Producto> deEsteColor = grupoColor.getValue();
                    String color = deEsteColor.get(0).getColor() != null ? deEsteColor.get(0).getColor().getNombre() : null;
                    String nombreProducto = unir(hoja, color);
                    ProductoMaster nuevo = productoMasterRepository.save(ProductoMaster.builder()
                            .nombreBase(NombreArticulo.construir(destino, nombreProducto))
                            .nombreProducto(nombreProducto)
                            .categoria(destino)
                            .tipo(tipoNuevo)
                            .marca(m.getMarca()).modelo(m.getModelo())
                            .especificaciones(m.getEspecificaciones()).notaAdicional(m.getNotaAdicional())
                            .compatibilidad(m.getCompatibilidad())
                            .tiempoEstimadoMin(m.getTiempoEstimadoMin())
                            .diasGarantia(m.getDiasGarantia())
                            .activo(m.getActivo())
                            .build());
                    for (Producto p : deEsteColor) {
                        p.setProductoMaster(nuevo);
                        productoRepository.save(p);
                    }
                    registrar(nuevo, nombreAnterior, "DIVIDIDO_POR_COLOR", deEsteColor.size(), destino);
                }
                m.setActivo(false); // el artículo anterior queda como historial de las ventas pasadas
                productoMasterRepository.save(m);
                for (DescuentoRegla regla : reglas) {
                    if (m.getIdprodmaster().equals(regla.getIdProductoMaster())) {
                        resultado.getAdvertencias().add("La regla de descuento #" + regla.getIddescuento() + " ('" + regla.getNombre()
                                + "') apuntaba a '" + nombreAnterior + "', que ahora se dividió por color: hay que volver a crearla por artículo.");
                    }
                }
            }
        }

        private void registrar(ProductoMaster m, String nombreAnterior, String accion, int productos, Categoria destino) {
            String nombre = m.getNombreBase();
            if (!nombresAsignados.add(clave(nombre)) && !nombre.equalsIgnoreCase(nombreAnterior)) {
                resultado.getAdvertencias().add("Nombre repetido tras migrar: '" + nombre + "' (artículo anterior '" + nombreAnterior + "').");
            }
            StringBuilder ruta = new StringBuilder();
            for (Categoria c : destino.ruta()) ruta.append(ruta.length() == 0 ? "" : " > ").append(c.getNombre());
            resultado.getLineas().add(new Linea(m.getIdprodmaster(), nombreAnterior, nombre, ruta.toString(), accion, productos));
            resultado.setMigrados(resultado.getMigrados() + 1);
        }

        // ── Categorías ────────────────────────────────────────────────────────

        private Grupo grupoDe(TipoProducto tipo) {
            return switch (tipo) {
                case CELULAR, TABLET -> Grupo.EQUIPOS;
                case ACCESORIO -> Grupo.ACCESORIOS;
                case SERVICIO -> Grupo.SERVICIOS;
            };
        }

        private String nombreDe(Grupo grupo) {
            return switch (grupo) { case EQUIPOS -> "Equipos"; case ACCESORIOS -> "Accesorios"; case SERVICIOS -> "Servicios"; };
        }

        private TipoProducto tipoDe(Grupo grupo) {
            return switch (grupo) { case EQUIPOS -> TipoProducto.CELULAR; case ACCESORIOS -> TipoProducto.ACCESORIO; case SERVICIOS -> TipoProducto.SERVICIO; };
        }

        /** La raíz que ya es (o puede pasar a ser) la categoría principal del grupo, sin tocar nada; null si hay que crearla. */
        private Categoria candidata(Grupo grupo) {
            String nombre = nombreDe(grupo);
            TipoProducto tipo = tipoDe(grupo);
            List<String> alias = switch (grupo) {
                case EQUIPOS -> List.of("equipos", "equipo", "celular", "celulares");
                case ACCESORIOS -> List.of("accesorios", "accesorio");
                case SERVICIOS -> List.of("servicios", "servicio");
            };
            Categoria nueva = todas.stream()
                    .filter(c -> c.getCategoriaSuperior() == null && Boolean.FALSE.equals(c.getIncluirEnNombre()) && c.getTipo() == tipo
                            && c.getNombre().equalsIgnoreCase(nombre))
                    .findFirst().orElse(null);
            if (nueva != null) return nueva;
            return todas.stream()
                    .filter(c -> c.getCategoriaSuperior() == null && !Boolean.FALSE.equals(c.getIncluirEnNombre()) && c.getTipo() == tipo
                            && alias.contains(c.getNombre().trim().toLowerCase()))
                    .findFirst().orElse(null);
        }

        /** La categoría principal del grupo: una ya migrada, la raíz anterior que le corresponde (se renombra) o una nueva. */
        private Categoria principal(Grupo grupo) {
            Categoria ya = principales.get(grupo);
            if (ya != null) return ya;
            String nombre = nombreDe(grupo);
            Categoria elegida = candidata(grupo);
            if (elegida != null && !Boolean.FALSE.equals(elegida.getIncluirEnNombre())) {
                resultado.getCategoriasCreadas().add("Principal: '" + elegida.getNombre() + "' pasa a llamarse '" + nombre + "'");
                if (elegida.getCodigo() != null) codigoAnteriorDeRaiz.put(elegida.getIdcat(), elegida.getCodigo());
                elegida.setNombre(nombre);
                elegida.setCodigo(null); // la principal no lleva prefijo: eso lo define la subcategoría 1
            }
            if (elegida == null) {
                elegida = Categoria.builder().nombre(nombre).tipo(tipoDe(grupo)).activo(true).build();
                resultado.getCategoriasCreadas().add("Principal: '" + nombre + "' (nueva)");
            }
            elegida.setIncluirEnNombre(false);
            elegida.setActivo(true);
            elegida = categoriaRepository.saveAndFlush(elegida);
            if (!todas.contains(elegida)) todas.add(elegida);
            principales.put(grupo, elegida);
            return elegida;
        }

        /** Subcategoría con ese nombre bajo el padre: la existente (se reactiva) o una nueva con ese código. */
        private Categoria hijo(Categoria padre, String nombre, String codigo, boolean incluir) {
            Categoria existente = todas.stream()
                    .filter(c -> c.getCategoriaSuperior() != null && c.getCategoriaSuperior().getIdcat().equals(padre.getIdcat())
                            && c.getNombre().equalsIgnoreCase(nombre))
                    .findFirst().orElse(null);
            if (existente != null) {
                if (!Boolean.TRUE.equals(existente.getActivo())) {
                    existente.setActivo(true);
                    categoriaRepository.save(existente);
                }
                sembrarContador(existente);
                return existente;
            }
            String codigoLibre = codigo;
            if (codigo != null) {
                for (Categoria otra : todas) {
                    if (codigo.equalsIgnoreCase(otra.getCodigo())) {
                        if (otra.getActivo() != null && otra.getActivo() && otra.nivel() >= 2
                                && otra.raiz() != null && Boolean.FALSE.equals(otra.raiz().getIncluirEnNombre())) {
                            codigoLibre = null; // lo usa una subcategoría vigente del árbol: no se duplica
                            resultado.getAdvertencias().add("'" + nombre + "' quedó sin código corto porque '" + codigo
                                    + "' ya es de '" + otra.getNombre() + "'. Asígnale uno antes de registrar artículos en ella.");
                        } else {
                            otra.setCodigo(null); // categoría anterior que se retira: cede su prefijo
                            categoriaRepository.save(otra);
                        }
                    }
                }
            }
            Categoria nueva = categoriaRepository.saveAndFlush(Categoria.builder()
                    .nombre(nombre).codigo(codigoLibre).tipo(padre.getTipo())
                    .categoriaSuperior(padre).incluirEnNombre(incluir).activo(true).build());
            todas.add(nueva);
            resultado.getCategoriasCreadas().add("'" + nombre + "' bajo '" + padre.getNombre() + "'" + (codigoLibre != null ? " (código " + codigoLibre + ")" : ""));
            sembrarContador(nueva);
            return nueva;
        }

        /**
         * Subcategoría 1 y 2 de un accesorio o servicio a partir de la categoría anterior. Cuelga de la principal así:
         * si la categoría anterior ya estaba bajo la raíz que ahora es la principal, se queda donde está; si venía de otra
         * raíz, esa raíz pasa a ser una subcategoría 1 nueva (con su código).
         */
        private Categoria[] nivelesAntiguos(ProductoMaster m, Categoria principal) {
            Categoria sub1;
            Categoria sub2 = null;
            List<Categoria> ruta = m.getCategoria() != null ? m.getCategoria().ruta() : List.of();
            if (ruta.isEmpty() || ruta.get(0).getIdcat().equals(principal.getIdcat())) {
                if (ruta.size() >= 2) {
                    sub1 = ruta.get(1);
                    sub1.setIncluirEnNombre(incluirSub1.getOrDefault(clave(sub1.getNombre()), principal.getTipo() != TipoProducto.ACCESORIO));
                    categoriaRepository.save(sub1);
                    sembrarContador(sub1);
                    if (ruta.size() >= 3) {
                        sub2 = ruta.get(2);
                        sub2.setIncluirEnNombre(false); // nombres ya curados
                        categoriaRepository.save(sub2);
                    }
                } else {
                    String codigo = codigoAnteriorDeRaiz.getOrDefault(principal.getIdcat(),
                            principal.getTipo() == TipoProducto.SERVICIO ? "SRV" : "ACC");
                    sub1 = hijo(principal, "General", codigo, false); // agrupa lo que colgaba de la raíz: no entra en el nombre
                }
            } else {
                Categoria raizAnterior = ruta.get(0);
                sub1 = hijo(principal, raizAnterior.getNombre(), raizAnterior.getCodigo(),
                        incluirSub1.getOrDefault(clave(raizAnterior.getNombre()), principal.getTipo() != TipoProducto.ACCESORIO));
                if (ruta.size() >= 2) sub2 = hijo(sub1, ruta.get(1).getNombre(), null, false);
                if (ruta.size() >= 3) {
                    resultado.getAdvertencias().add("'" + m.getNombreBase() + "' estaba en " + ruta.size()
                            + " niveles de categorías anteriores; se omitió '" + ruta.get(2).getNombre() + "'.");
                }
            }
            return new Categoria[]{sub1, sub2};
        }

        /** Que el contador de la subcategoría 1 siga donde iban los códigos de ese prefijo (CEL-000123 → próximo 124). */
        private void sembrarContador(Categoria sub1) {
            if (sub1.nivel() != 2 || sub1.getCodigo() == null || sub1.getCodigo().isBlank() || sub1.getIdcat() == null) return;
            String prefijo = sub1.getCodigo().trim().toUpperCase();
            long maximo = 0;
            for (String codpro : productoRepository.codigosConPrefijo(prefijo)) {
                String sufijo = codpro.substring(prefijo.length() + 1);
                if (sufijo.matches("\\d{1,12}")) maximo = Math.max(maximo, Long.parseLong(sufijo));
            }
            if ("CEL".equals(prefijo)) {
                maximo = Math.max(maximo, categoriaFolioRepository.findById(FOLIO_EQUIPOS)
                        .map(f -> f.getUltimoFolio() == null ? 0L : f.getUltimoFolio()).orElse(0L));
            }
            if (maximo > 0) categoriaFolioRepository.fijarMinimo(sub1.getIdcat(), maximo);
        }

        /** Las categorías anteriores (raíces ajenas al árbol nuevo) que quedaron sin artículos se desactivan junto con sus hijas. */
        private void desactivarCategoriasAntiguasVacias(List<ProductoMaster> masters) {
            Set<Short> principalesIds = new HashSet<>();
            principales.values().forEach(c -> principalesIds.add(c.getIdcat()));
            Set<Short> conArticulos = new HashSet<>();
            for (ProductoMaster m : masters) {
                if (m.getCategoria() == null) continue;
                for (Categoria c : m.getCategoria().ruta()) conArticulos.add(c.getIdcat());
            }
            for (Categoria c : new ArrayList<>(todas)) {
                if (!Boolean.TRUE.equals(c.getActivo())) continue;
                Categoria raiz = c.raiz();
                if (raiz == null || principalesIds.contains(raiz.getIdcat()) || Boolean.FALSE.equals(raiz.getIncluirEnNombre())) continue;
                if (conArticulos.contains(c.getIdcat())) continue;
                c.setActivo(false);
                categoriaRepository.save(c);
                resultado.getCategoriasDesactivadas().add(c.getNombre());
            }
        }

        // ── Utilidades de texto ───────────────────────────────────────────────

        private String limpiar(String s) {
            if (s == null) return null;
            String t = s.trim().replaceAll("\\s+", " ");
            return t.isEmpty() ? null : t;
        }

        private String unir(String a, String b) {
            String t = ((a == null ? "" : a) + " " + (b == null ? "" : b)).trim().replaceAll("\\s+", " ");
            return t.isEmpty() ? null : t;
        }

        /** Quita de la hoja el nombre de la categoría si ya empieza con él, para no repetirlo ("Fundas Fundas iPhone"). */
        private String sinPrefijo(String texto, String prefijo) {
            if (texto == null) return null;
            String t = texto.trim();
            if (prefijo != null && t.toLowerCase().startsWith(prefijo.trim().toLowerCase() + " ")) {
                t = t.substring(prefijo.trim().length()).trim();
            }
            return t.isEmpty() ? null : t;
        }

        /** Lo que queda del nombre después del prefijo (sin separadores como "—"), "" si es idéntico, null si no empieza con él. */
        private String restoTrasPrefijo(String nombre, String prefijo) {
            if (nombre == null || prefijo == null || prefijo.isBlank()) return null;
            String n = nombre.trim().replaceAll("\\s+", " ");
            if (!n.toLowerCase().startsWith(prefijo.toLowerCase())) return null;
            String resto = n.substring(prefijo.length());
            if (!resto.isEmpty() && Character.isLetterOrDigit(resto.charAt(0))) return null; // "Fundas" no es prefijo de "Fundamental"
            return resto.replaceFirst("^[\\s\\-—–:/]+", "").trim();
        }

        private String clave(String nombre) {
            return nombre == null ? "" : nombre.trim().toLowerCase();
        }
    }
}
