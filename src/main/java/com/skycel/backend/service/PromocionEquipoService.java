package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.PromocionEquipo;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.promocion.PromocionEquipoDto;
import com.skycel.backend.repository.CategoriaRepository;
import com.skycel.backend.repository.PromocionEquipoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * La promoción al vender un equipo: por cada celular o tablet que se lleva el cliente, un chip (Telcel o Bait) de regalo, una mica
 * con descuento (si cuesta eso o menos va de regalo) y un protector con descuento si cuesta poco. La administra un administrador
 * (como los descuentos) y la aplica {@link VentaService} sola al vender: nadie captura estos descuentos a mano.
 */
@Service
@RequiredArgsConstructor
public class PromocionEquipoService {

    private static final BigDecimal DESCUENTO_MICA = new BigDecimal("50");
    private static final BigDecimal DESCUENTO_PROTECTOR = new BigDecimal("100");
    private static final BigDecimal PROTECTOR_MENOR_A = new BigDecimal("249");

    private final PromocionEquipoRepository repository;
    private final CategoriaRepository categoriaRepository;

    /** Una línea de la venta tal como la ve la promoción. */
    public record EntradaLinea(TipoProducto tipo, Set<Short> categorias, BigDecimal precioBase, int cantidad) {}

    /** Cuántas piezas de la línea reciben la promoción y cuánto se le descuenta a cada una (0 piezas = sin promoción). */
    public record Beneficio(int piezas, BigDecimal descuentoPorPieza, String tipo) {
        public static final Beneficio NINGUNO = new Beneficio(0, BigDecimal.ZERO, null);

        public Beneficio(int piezas, BigDecimal descuentoPorPieza) { this(piezas, descuentoPorPieza, null); }
    }

    /** Lo que ya cuenta de las otras ventas del mismo carrito: equipos vendidos y piezas ya regaladas o rebajadas de cada cosa. */
    public record Previos(int equipos, int chips, int micas, int protectores) {
        public static final Previos NINGUNOS = new Previos(0, 0, 0, 0);
    }

    // ── Cálculo ───────────────────────────────────────────────────────────────

    /** Los ids de la categoría y de todas las que la contienen: una categoría configurada vale para sus subcategorías. */
    public static Set<Short> ruta(Categoria categoria) {
        Set<Short> ids = new HashSet<>();
        for (Categoria c = categoria; c != null && ids.add(c.getIdcat()); c = c.getCategoriaSuperior()) { /* sube hasta la principal */ }
        return ids;
    }

    /** La promoción que corresponde a cada línea de la venta, en el mismo orden. Las piezas se reparten por orden de aparición. */
    @Transactional
    public List<Beneficio> calcularConPrevios(List<EntradaLinea> lineas, Previos previos) {
        return calcular(obtenerEntidad(), lineas, previos);
    }

    public static List<Beneficio> calcular(PromocionEquipo p, List<EntradaLinea> lineas) {
        return calcular(p, lineas, Previos.NINGUNOS);
    }

    public static List<Beneficio> calcular(PromocionEquipo p, List<EntradaLinea> lineas, Previos previos) {
        List<Beneficio> resultado = new ArrayList<>();
        int equipos = previos.equipos();
        for (EntradaLinea l : lineas) if (esEquipo(l.tipo())) equipos += l.cantidad();
        boolean aplica = Boolean.TRUE.equals(p.getActivo()) && equipos > 0;
        int chips = Math.max(0, equipos * p.getChipsPorEquipo() - previos.chips());
        int micas = Math.max(0, equipos * p.getMicasPorEquipo() - previos.micas());
        int protectores = Math.max(0, equipos * p.getProtectoresPorEquipo() - previos.protectores());

        for (EntradaLinea l : lineas) {
            Beneficio b = Beneficio.NINGUNO;
            if (aplica && !esEquipo(l.tipo()) && l.precioBase() != null && l.precioBase().signum() > 0) {
                if (en(l, p.getIdCategoriaChip()) && chips > 0) {
                    int piezas = Math.min(l.cantidad(), chips);
                    chips -= piezas;
                    b = new Beneficio(piezas, l.precioBase(), "CHIP");                // el chip va de regalo
                } else if (en(l, p.getIdCategoriaMica()) && micas > 0) {
                    int piezas = Math.min(l.cantidad(), micas);
                    micas -= piezas;
                    b = new Beneficio(piezas, l.precioBase().min(p.getDescuentoMica()), "MICA");
                } else if (en(l, p.getIdCategoriaProtector()) && protectores > 0
                        && l.precioBase().compareTo(p.getProtectorPrecioMenorA()) < 0) {
                    int piezas = Math.min(l.cantidad(), protectores);
                    protectores -= piezas;
                    b = new Beneficio(piezas, l.precioBase().min(p.getDescuentoProtector()), "PROTECTOR");
                }
            }
            resultado.add(b);
        }
        return resultado;
    }

    private static boolean esEquipo(TipoProducto tipo) {
        return tipo == TipoProducto.CELULAR || tipo == TipoProducto.TABLET;
    }

    private static boolean en(EntradaLinea l, Short idCategoria) {
        return idCategoria != null && l.categorias() != null && l.categorias().contains(idCategoria);
    }

    // ── Configuración ─────────────────────────────────────────────────────────

    @Transactional
    public PromocionEquipoDto obtener() {
        return aDto(obtenerEntidad());
    }

    @Transactional
    public PromocionEquipoDto actualizar(PromocionEquipoDto dto) {
        PromocionEquipo p = obtenerEntidad();
        BigDecimal mica = valor(dto.getDescuentoMica(), "El descuento de la mica");
        BigDecimal protector = valor(dto.getDescuentoProtector(), "El descuento del protector");
        BigDecimal menorA = valor(dto.getProtectorPrecioMenorA(), "El precio máximo del protector");
        int chips = piezas(dto.getChipsPorEquipo(), "chips");
        int micas = piezas(dto.getMicasPorEquipo(), "micas");
        int protectores = piezas(dto.getProtectoresPorEquipo(), "protectores");
        p.setActivo(dto.getActivo() == null || dto.getActivo());
        p.setIdCategoriaChip(existente(dto.getIdCategoriaChip(), "chips"));
        p.setIdCategoriaMica(existente(dto.getIdCategoriaMica(), "micas"));
        p.setIdCategoriaProtector(existente(dto.getIdCategoriaProtector(), "protectores"));
        p.setDescuentoMica(mica);
        p.setDescuentoProtector(protector);
        p.setProtectorPrecioMenorA(menorA);
        p.setChipsPorEquipo(chips);
        p.setMicasPorEquipo(micas);
        p.setProtectoresPorEquipo(protectores);
        return aDto(repository.save(p));
    }

    private BigDecimal valor(BigDecimal v, String que) {
        if (v == null || v.signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + " debe ser un monto de $0 o más.");
        return v;
    }

    private int piezas(Integer v, String que) {
        if (v == null || v < 0 || v > 10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las piezas de " + que + " por equipo van de 0 a 10.");
        return v;
    }

    private Short existente(Short idcat, String que) {
        if (idcat == null) return null;
        if (!categoriaRepository.existsById(idcat))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría elegida para " + que + " no existe.");
        return idcat;
    }

    /** La fila única; si todavía no existe se crea con lo acordado y, por nombre, las categorías de chips, micas y protectores. */
    private PromocionEquipo obtenerEntidad() {
        return repository.findById(PromocionEquipo.ID_UNICO).orElseGet(() -> {
            List<Categoria> categorias = categoriaRepository.findByActivoTrue();
            return repository.save(PromocionEquipo.builder()
                    .id(PromocionEquipo.ID_UNICO)
                    .activo(true)
                    .idCategoriaChip(buscarPorNombre(categorias, "chip"))
                    .idCategoriaMica(buscarPorNombre(categorias, "mica"))
                    .idCategoriaProtector(buscarPorNombre(categorias, "protector"))
                    .descuentoMica(DESCUENTO_MICA)
                    .descuentoProtector(DESCUENTO_PROTECTOR)
                    .protectorPrecioMenorA(PROTECTOR_MENOR_A)
                    .chipsPorEquipo(1)
                    .micasPorEquipo(1)
                    .protectoresPorEquipo(1)
                    .build());
        });
    }

    /** La categoría más alta del árbol cuyo nombre empieza con la palabra (así «Mica» y «Micas» dan la misma). */
    private static Short buscarPorNombre(List<Categoria> categorias, String palabra) {
        return categorias.stream()
                .filter(c -> c.getNombre() != null && c.getNombre().toLowerCase().startsWith(palabra))
                .min(Comparator.comparingInt(PromocionEquipoService::profundidad).thenComparing(Categoria::getIdcat))
                .map(Categoria::getIdcat).orElse(null);
    }

    private static int profundidad(Categoria c) {
        int n = 0;
        for (Categoria x = c.getCategoriaSuperior(); x != null && n < 10; x = x.getCategoriaSuperior()) n++;
        return n;
    }

    private PromocionEquipoDto aDto(PromocionEquipo p) {
        PromocionEquipoDto dto = new PromocionEquipoDto();
        dto.setActivo(p.getActivo());
        dto.setIdCategoriaChip(p.getIdCategoriaChip());
        dto.setIdCategoriaMica(p.getIdCategoriaMica());
        dto.setIdCategoriaProtector(p.getIdCategoriaProtector());
        dto.setCategoriaChip(nombre(p.getIdCategoriaChip()));
        dto.setCategoriaMica(nombre(p.getIdCategoriaMica()));
        dto.setCategoriaProtector(nombre(p.getIdCategoriaProtector()));
        dto.setDescuentoMica(p.getDescuentoMica());
        dto.setDescuentoProtector(p.getDescuentoProtector());
        dto.setProtectorPrecioMenorA(p.getProtectorPrecioMenorA());
        dto.setChipsPorEquipo(p.getChipsPorEquipo());
        dto.setMicasPorEquipo(p.getMicasPorEquipo());
        dto.setProtectoresPorEquipo(p.getProtectoresPorEquipo());
        return dto;
    }

    private String nombre(Short idcat) {
        return idcat == null ? null : categoriaRepository.findById(idcat).map(Categoria::getNombre).orElse(null);
    }
}
