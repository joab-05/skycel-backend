package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Categoria;
import com.skycel.backend.domain.entity.PromocionEquipo;
import com.skycel.backend.domain.entity.PromocionEquipoRegla;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.dto.promocion.PromocionEquipoDto;
import com.skycel.backend.dto.promocion.PromocionReglasDto;
import com.skycel.backend.repository.CategoriaRepository;
import com.skycel.backend.repository.PromocionEquipoReglaRepository;
import com.skycel.backend.repository.PromocionEquipoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * La promoción al vender un equipo: por cada celular o tablet que se lleva el cliente, ciertos artículos (chips, micas, protectores,
 * lo que se defina) reciben un descuento: de regalo, un porcentaje, un monto o un precio fijo, con el redondeo que se quiera. Se
 * define con una lista de reglas (categoría, rango de precio, piezas por equipo, descuento y redondeo) que administra un administrador
 * (como los descuentos) y que aplica {@link VentaService} sola al vender: nadie captura estos descuentos a mano.
 *
 * Las reglas se revisan en orden y a cada pieza le toca la primera que le corresponde y todavía tiene cupo. La primera versión de la
 * promoción (chip de regalo, mica y protector con descuento fijo) sigue disponible como configuración plana para los equipos que aún no
 * se actualizan: sus tres reglas se guardan con claves CHIP, MICA y PROTECTOR y los dos formatos se mantienen sincronizados.
 */
@Service
@RequiredArgsConstructor
public class PromocionEquipoService {

    private static final BigDecimal DESCUENTO_MICA = new BigDecimal("50");
    private static final BigDecimal DESCUENTO_PROTECTOR = new BigDecimal("100");
    private static final BigDecimal PROTECTOR_MENOR_A = new BigDecimal("249");
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal UN_CENTAVO = new BigDecimal("0.01");
    private static final int MAX_REGLAS = 30;

    public static final Set<String> TIPOS = Set.of("REGALO", "PORCENTAJE", "MONTO", "PRECIO_FINAL");
    public static final Set<String> REDONDEOS = Set.of("NINGUNO", "PESO_ARRIBA", "PESO_ABAJO", "CINCO_ARRIBA", "CINCO_ABAJO", "DIEZ_ARRIBA", "DIEZ_ABAJO");

    private final PromocionEquipoRepository repository;
    private final PromocionEquipoReglaRepository reglaRepository;
    private final CategoriaRepository categoriaRepository;

    /** Una línea de la venta tal como la ve la promoción. */
    public record EntradaLinea(TipoProducto tipo, Set<Short> categorias, BigDecimal precioBase, int cantidad) {}

    /** Cuántas piezas de la línea reciben la promoción y cuánto se le descuenta a cada una (0 piezas = sin promoción); tipo = clave de la regla. */
    public record Beneficio(int piezas, BigDecimal descuentoPorPieza, String tipo) {
        public static final Beneficio NINGUNO = new Beneficio(0, BigDecimal.ZERO, null);
        public Beneficio(int piezas, BigDecimal descuentoPorPieza) { this(piezas, descuentoPorPieza, null); }
    }

    /** Lo que ya cuenta de las otras ventas del mismo carrito: equipos vendidos y piezas que ya recibió cada regla (por su clave). */
    public record Previos(int equipos, Map<String, Integer> usadas) {
        public static final Previos NINGUNOS = new Previos(0, Map.of());

        /** La forma de la primera versión: chips, micas y protectores ya entregados. */
        public Previos(int equipos, int chips, int micas, int protectores) {
            this(equipos, Map.of("CHIP", chips, "MICA", micas, "PROTECTOR", protectores));
        }

        int usadas(String clave) { return usadas.getOrDefault(clave, 0); }
    }

    /** Una regla lista para calcular (sin base de datos). */
    public record Regla(String clave, Short idCategoria, BigDecimal desde, BigDecimal hasta, int piezasPorEquipo,
                        String tipo, BigDecimal valor, String redondeo) {
        boolean aplicaA(EntradaLinea l) {
            if (idCategoria == null || l.categorias() == null || !l.categorias().contains(idCategoria)) return false;
            BigDecimal p = l.precioBase();
            return (desde == null || p.compareTo(desde) >= 0) && (hasta == null || p.compareTo(hasta) <= 0);
        }
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
        PromocionEquipo p = obtenerEntidad();
        return calcular(Boolean.TRUE.equals(p.getActivo()), reglasActivas(), lineas, previos);
    }

    /** Con la configuración plana de la primera versión (chip, mica y protector). */
    public static List<Beneficio> calcular(PromocionEquipo p, List<EntradaLinea> lineas) {
        return calcular(p, lineas, Previos.NINGUNOS);
    }

    public static List<Beneficio> calcular(PromocionEquipo p, List<EntradaLinea> lineas, Previos previos) {
        return calcular(Boolean.TRUE.equals(p.getActivo()), reglasDeLaConfiguracionPlana(p), lineas, previos);
    }

    public static List<Beneficio> calcular(boolean activo, List<Regla> reglas, List<EntradaLinea> lineas, Previos previos) {
        List<Beneficio> resultado = new ArrayList<>();
        int equipos = previos.equipos();
        for (EntradaLinea l : lineas) if (esEquipo(l.tipo())) equipos += l.cantidad();
        boolean aplica = activo && equipos > 0;
        Map<String, Integer> cupo = new HashMap<>();
        if (aplica) for (Regla r : reglas) cupo.put(r.clave(), Math.max(0, equipos * r.piezasPorEquipo() - previos.usadas(r.clave())));
        for (EntradaLinea l : lineas) {
            Beneficio b = Beneficio.NINGUNO;
            if (aplica && !esEquipo(l.tipo()) && l.precioBase() != null && l.precioBase().signum() > 0) {
                for (Regla r : reglas) {
                    int disponibles = cupo.getOrDefault(r.clave(), 0);
                    if (disponibles <= 0 || !r.aplicaA(l)) continue;
                    int piezas = Math.min(l.cantidad(), disponibles);
                    cupo.put(r.clave(), disponibles - piezas);
                    b = new Beneficio(piezas, descuentoPorPieza(r, l.precioBase()), r.clave());
                    break;
                }
            }
            resultado.add(b);
        }
        return resultado;
    }

    /** Lo que se le descuenta a cada pieza: el precio de lista menos lo que paga con la promoción (ya redondeado). */
    public static BigDecimal descuentoPorPieza(Regla r, BigDecimal precio) {
        BigDecimal nuevo = switch (r.tipo()) {
            case "REGALO" -> BigDecimal.ZERO;
            case "PORCENTAJE" -> precio.multiply(CIEN.subtract(r.valor())).divide(CIEN, 2, RoundingMode.HALF_UP);
            case "MONTO" -> precio.subtract(r.valor().min(precio));
            default -> r.valor().min(precio);                                  // PRECIO_FINAL
        };
        if (!"REGALO".equals(r.tipo())) nuevo = redondear(nuevo, r.redondeo());
        nuevo = nuevo.max(BigDecimal.ZERO).min(precio);
        return precio.subtract(nuevo).setScale(2, RoundingMode.HALF_UP);
    }

    /** El precio que queda, al peso o al múltiplo de 5 o 10 más cercano hacia arriba o hacia abajo. */
    static BigDecimal redondear(BigDecimal precio, String redondeo) {
        if (redondeo == null || "NINGUNO".equals(redondeo)) return precio;
        BigDecimal paso = redondeo.startsWith("CINCO") ? new BigDecimal("5") : redondeo.startsWith("DIEZ") ? BigDecimal.TEN : BigDecimal.ONE;
        RoundingMode modo = redondeo.endsWith("ARRIBA") ? RoundingMode.CEILING : RoundingMode.FLOOR;
        return precio.divide(paso, 0, modo).multiply(paso).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean esEquipo(TipoProducto tipo) {
        return tipo == TipoProducto.CELULAR || tipo == TipoProducto.TABLET;
    }

    /** Las tres reglas de la primera versión, en su orden (chip, mica, protector). Las que no tienen categoría o piezas no cuentan. */
    public static List<Regla> reglasDeLaConfiguracionPlana(PromocionEquipo p) {
        List<Regla> r = new ArrayList<>();
        if (p.getIdCategoriaChip() != null && p.getChipsPorEquipo() > 0)
            r.add(new Regla("CHIP", p.getIdCategoriaChip(), null, null, p.getChipsPorEquipo(), "REGALO", BigDecimal.ZERO, "NINGUNO"));
        if (p.getIdCategoriaMica() != null && p.getMicasPorEquipo() > 0)
            r.add(new Regla("MICA", p.getIdCategoriaMica(), null, null, p.getMicasPorEquipo(), "MONTO", p.getDescuentoMica(), "NINGUNO"));
        if (p.getIdCategoriaProtector() != null && p.getProtectoresPorEquipo() > 0)
            r.add(new Regla("PROTECTOR", p.getIdCategoriaProtector(), null, p.getProtectorPrecioMenorA().subtract(UN_CENTAVO),
                    p.getProtectoresPorEquipo(), "MONTO", p.getDescuentoProtector(), "NINGUNO"));
        return r;
    }

    // ── Reglas ────────────────────────────────────────────────────────────────

    /** Las reglas que se aplican hoy, en orden. */
    private List<Regla> reglasActivas() {
        asegurarReglas();
        return reglaRepository.findByActivaTrueOrderByOrdenAscIdreglaAsc().stream()
                .map(r -> new Regla(r.clave(), r.getIdCategoria(), r.getPrecioDesde(), r.getPrecioHasta(), r.getPiezasPorEquipo(),
                        r.getTipo(), r.getValor(), r.getRedondeo()))
                .toList();
    }

    /** La primera vez, las reglas salen de la configuración plana que ya había (así nada cambia al actualizar). */
    private void asegurarReglas() {
        if (reglaRepository.count() > 0) return;
        PromocionEquipo p = obtenerEntidad();
        int orden = 1;
        reglaRepository.save(reglaBase("Chip de regalo", p.getIdCategoriaChip(), null, null, p.getChipsPorEquipo(), "REGALO", BigDecimal.ZERO, "CHIP", orden++));
        reglaRepository.save(reglaBase("Mica con descuento", p.getIdCategoriaMica(), null, null, p.getMicasPorEquipo(), "MONTO", p.getDescuentoMica(), "MICA", orden++));
        reglaRepository.save(reglaBase("Protector con descuento", p.getIdCategoriaProtector(), null,
                p.getProtectorPrecioMenorA().subtract(UN_CENTAVO), p.getProtectoresPorEquipo(), "MONTO", p.getDescuentoProtector(), "PROTECTOR", orden));
    }

    private static PromocionEquipoRegla reglaBase(String nombre, Short idcat, BigDecimal desde, BigDecimal hasta, int piezas, String tipo,
                                                  BigDecimal valor, String clave, int orden) {
        return PromocionEquipoRegla.builder().orden(orden).nombre(nombre).idCategoria(idcat != null ? idcat : 0)
                .precioDesde(desde).precioHasta(hasta).piezasPorEquipo(Math.max(piezas, 1)).tipo(tipo).valor(valor)
                .redondeo("NINGUNO").activa(idcat != null && piezas > 0).claveLegacy(clave).build();
    }

    @Transactional
    public PromocionReglasDto obtenerReglas() {
        asegurarReglas();
        PromocionReglasDto dto = new PromocionReglasDto();
        dto.setActivo(obtenerEntidad().getActivo());
        dto.setReglas(reglaRepository.findAllByOrderByOrdenAscIdreglaAsc().stream().map(this::aDto).toList());
        return dto;
    }

    /** Reemplaza la lista completa: las que traen id se actualizan, las nuevas se crean y las que ya no vienen se borran. */
    @Transactional
    public PromocionReglasDto guardarReglas(PromocionReglasDto dto) {
        asegurarReglas();
        List<PromocionReglasDto.ReglaDto> nuevas = dto.getReglas() == null ? List.of() : dto.getReglas();
        if (nuevas.size() > MAX_REGLAS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay demasiadas reglas (máximo " + MAX_REGLAS + ").");
        for (PromocionReglasDto.ReglaDto d : nuevas) validar(d);
        Map<Integer, PromocionEquipoRegla> existentes = new HashMap<>();
        for (PromocionEquipoRegla r : reglaRepository.findAll()) existentes.put(r.getIdregla(), r);
        Set<Integer> conservadas = new HashSet<>();
        int orden = 1;
        for (PromocionReglasDto.ReglaDto d : nuevas) {
            PromocionEquipoRegla r = d.getId() != null ? existentes.get(d.getId()) : null;
            if (d.getId() != null && r == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La regla " + d.getId() + " ya no existe: recargue la lista.");
            if (r == null) r = new PromocionEquipoRegla();
            else conservadas.add(r.getIdregla());
            r.setOrden(orden++);
            r.setNombre(d.getNombre().trim());
            r.setIdCategoria(d.getIdCategoria() != null ? d.getIdCategoria() : 0);   // 0 = sin categoría (solo una regla apagada)
            r.setPrecioDesde(d.getPrecioDesde());
            r.setPrecioHasta(d.getPrecioHasta());
            r.setPiezasPorEquipo(d.getPiezasPorEquipo());
            r.setTipo(d.getTipo());
            r.setValor("REGALO".equals(d.getTipo()) ? BigDecimal.ZERO : d.getValor());
            r.setRedondeo("REGALO".equals(d.getTipo()) || d.getRedondeo() == null ? "NINGUNO" : d.getRedondeo());
            r.setActiva(d.getActiva() == null || d.getActiva());
            reglaRepository.save(r);
        }
        for (PromocionEquipoRegla r : existentes.values()) if (!conservadas.contains(r.getIdregla())) reglaRepository.delete(r);
        PromocionEquipo p = obtenerEntidad();
        p.setActivo(dto.getActivo() == null || dto.getActivo());
        sincronizarConfiguracionPlana(p, reglaRepository.findAllByOrderByOrdenAscIdreglaAsc());
        repository.save(p);
        return obtenerReglas();
    }

    private void validar(PromocionReglasDto.ReglaDto d) {
        if (d.getNombre() == null || d.getNombre().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada regla necesita un nombre.");
        String que = "La regla «" + d.getNombre().trim() + "»";
        if (d.getNombre().trim().length() > 80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": el nombre no puede exceder los 80 caracteres.");
        boolean encendida = d.getActiva() == null || d.getActiva();
        if (d.getIdCategoria() == null ? encendida : !categoriaRepository.existsById(d.getIdCategoria()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": elija la categoría de los artículos.");
        if (d.getPiezasPorEquipo() == null || d.getPiezasPorEquipo() < 1 || d.getPiezasPorEquipo() > 10)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": las piezas por equipo van de 1 a 10.");
        if (d.getTipo() == null || !TIPOS.contains(d.getTipo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": el tipo de descuento debe ser REGALO, PORCENTAJE, MONTO o PRECIO_FINAL.");
        if (d.getRedondeo() != null && !REDONDEOS.contains(d.getRedondeo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": el redondeo no es válido.");
        if ((d.getPrecioDesde() != null && d.getPrecioDesde().signum() < 0) || (d.getPrecioHasta() != null && d.getPrecioHasta().signum() < 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": el rango de precio no puede ser negativo.");
        if (d.getPrecioDesde() != null && d.getPrecioHasta() != null && d.getPrecioDesde().compareTo(d.getPrecioHasta()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": el precio inicial no puede ser mayor al final.");
        BigDecimal v = d.getValor();
        switch (d.getTipo()) {
            case "PORCENTAJE" -> {
                if (v == null || v.signum() <= 0 || v.compareTo(CIEN) > 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": el porcentaje de descuento va de más de 0 a 100.");
            }
            case "MONTO" -> {
                if (v == null || v.signum() <= 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": el descuento en pesos debe ser mayor a $0.");
            }
            case "PRECIO_FINAL" -> {
                if (v == null || v.signum() < 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, que + ": el precio final debe ser de $0 o más.");
            }
            default -> { /* REGALO no lleva valor */ }
        }
    }

    private PromocionReglasDto.ReglaDto aDto(PromocionEquipoRegla r) {
        PromocionReglasDto.ReglaDto d = new PromocionReglasDto.ReglaDto();
        d.setId(r.getIdregla());
        d.setNombre(r.getNombre());
        d.setIdCategoria(r.getIdCategoria() == 0 ? null : r.getIdCategoria());
        d.setCategoria(nombre(r.getIdCategoria()));
        d.setPrecioDesde(r.getPrecioDesde());
        d.setPrecioHasta(r.getPrecioHasta());
        d.setPiezasPorEquipo(r.getPiezasPorEquipo());
        d.setTipo(r.getTipo());
        d.setValor(r.getValor());
        d.setRedondeo(r.getRedondeo());
        d.setActiva(r.getActiva());
        return d;
    }

    // ── Configuración plana de la primera versión ─────────────────────────────

    @Transactional
    public PromocionEquipoDto obtener() {
        return aDto(obtenerEntidad());
    }

    @Transactional
    public PromocionEquipoDto actualizar(PromocionEquipoDto dto) {
        asegurarReglas();
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
        repository.save(p);
        // Las tres reglas de la primera versión se ponen igual a lo que se acaba de guardar
        for (PromocionEquipoRegla r : reglaRepository.findAllByOrderByOrdenAscIdreglaAsc()) {
            if (r.getClaveLegacy() == null) continue;
            switch (r.getClaveLegacy()) {
                case "CHIP" -> igualarRegla(r, p.getIdCategoriaChip(), chips, "REGALO", BigDecimal.ZERO, null);
                case "MICA" -> igualarRegla(r, p.getIdCategoriaMica(), micas, "MONTO", mica, null);
                case "PROTECTOR" -> igualarRegla(r, p.getIdCategoriaProtector(), protectores, "MONTO", protector, menorA.subtract(UN_CENTAVO));
                default -> { }
            }
        }
        return aDto(p);
    }

    private void igualarRegla(PromocionEquipoRegla r, Short idcat, int piezas, String tipo, BigDecimal valor, BigDecimal hasta) {
        if (idcat != null) r.setIdCategoria(idcat);
        if (piezas > 0) r.setPiezasPorEquipo(piezas);
        r.setActiva(idcat != null && piezas > 0);
        r.setTipo(tipo);
        r.setValor(valor);
        r.setPrecioDesde(null);
        r.setPrecioHasta(hasta);
        r.setRedondeo("NINGUNO");
        reglaRepository.save(r);
    }

    /** Lo que ven los equipos que todavía leen la configuración plana: lo que valen hoy las tres reglas de la primera versión. */
    private void sincronizarConfiguracionPlana(PromocionEquipo p, List<PromocionEquipoRegla> reglas) {
        // Si se quitó alguna de las tres, para esos equipos ya no hay promoción de ese tipo
        Set<String> presentes = new HashSet<>();
        for (PromocionEquipoRegla r : reglas) if (r.getClaveLegacy() != null) presentes.add(r.getClaveLegacy());
        if (!presentes.contains("CHIP")) p.setChipsPorEquipo(0);
        if (!presentes.contains("MICA")) p.setMicasPorEquipo(0);
        if (!presentes.contains("PROTECTOR")) p.setProtectoresPorEquipo(0);
        for (PromocionEquipoRegla r : reglas) {
            if (r.getClaveLegacy() == null) continue;
            int piezas = Boolean.TRUE.equals(r.getActiva()) ? r.getPiezasPorEquipo() : 0;
            Short idcat = r.getIdCategoria() == 0 ? null : r.getIdCategoria();
            switch (r.getClaveLegacy()) {
                case "CHIP" -> { p.setIdCategoriaChip(idcat); p.setChipsPorEquipo(piezas); }
                case "MICA" -> {
                    p.setIdCategoriaMica(idcat);
                    p.setMicasPorEquipo(piezas);
                    if ("MONTO".equals(r.getTipo())) p.setDescuentoMica(r.getValor());
                }
                case "PROTECTOR" -> {
                    p.setIdCategoriaProtector(idcat);
                    p.setProtectoresPorEquipo(piezas);
                    if ("MONTO".equals(r.getTipo())) p.setDescuentoProtector(r.getValor());
                    if (r.getPrecioHasta() != null) p.setProtectorPrecioMenorA(r.getPrecioHasta().add(UN_CENTAVO));
                }
                default -> { }
            }
        }
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
        return idcat == null || idcat == 0 ? null : categoriaRepository.findById(idcat).map(Categoria::getNombre).orElse(null);
    }
}
