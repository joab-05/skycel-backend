package com.skycel.backend.service;

import com.skycel.backend.domain.entity.MovimientoInventario;
import com.skycel.backend.domain.entity.ProductoImei;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Permiso;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.producto.MermaReporteDto;
import com.skycel.backend.dto.producto.MermaReporteDto.MovimientoDto;
import com.skycel.backend.dto.producto.MermaReporteDto.ResumenDto;
import com.skycel.backend.repository.MovimientoInventarioRepository;
import com.skycel.backend.repository.ProductoImeiRepository;
import com.skycel.backend.repository.TiendaRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Control de mermas: junta, de la bitácora de existencias, todo lo que cambió el inventario por fuera de una venta, una devolución o un
 * traspaso (salidas y ajustes manuales, bajas de equipos, ajustes de un inventario físico), y lo valúa a costo. Es lo que un
 * administrador revisa para ver quién ajustó qué, por qué y cuánto dinero representa.
 */
@Service
@RequiredArgsConstructor
public class MermaService {

    /** Tipos del kardex que cuentan como merma o ajuste (lo demás —ventas, traspasos, altas— tiene su propio control). */
    public static final List<String> TIPOS = List.of("SALIDA", "AJUSTE");

    private final MovimientoInventarioRepository movimientoRepository;
    private final ProductoImeiRepository imeiRepository;
    private final TiendaRepository tiendaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    @Transactional(readOnly = true)
    public MermaReporteDto reporte(Integer codti, LocalDate desde, LocalDate hasta, String username) {
        Usuario u = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        boolean admin = u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
        Integer tienda = codti;
        if (!admin) {
            if (u.getTienda() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene una tienda asignada.");
            tienda = u.getTienda().getCodti();   // un encargado solo ve la suya
        }
        boolean conCostos = permisoService.tiene(u.getRol(), Permiso.COSTOS_VER.name());
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.minusDays(30);
        LocalDateTime ini1 = ini.atStartOfDay(), fin1 = fin.plusDays(1).atStartOfDay().minusNanos(1);

        Map<Integer, String> nombresTienda = new LinkedHashMap<>();
        tiendaRepository.findAll().forEach(t -> nombresTienda.put(t.getCodti(), t.getNombre()));

        List<MovimientoDto> detalle = new ArrayList<>();
        BigDecimal perdida = BigDecimal.ZERO, sobrante = BigDecimal.ZERO;
        for (MovimientoInventario m : movimientoRepository.mermas(tienda, TIPOS, ini1, fin1)) {
            BigDecimal costo = costoDe(m);
            BigDecimal valor = conCostos && costo != null ? costo.multiply(m.getCantidad()) : null;
            if (valor != null) {
                if (valor.signum() < 0) perdida = perdida.add(valor.negate()); else sobrante = sobrante.add(valor);
            }
            String nombre = m.getProductoMaster() != null ? m.getProductoMaster().getNombreBase()
                    : m.getProducto() != null && m.getProducto().getProductoMaster() != null ? m.getProducto().getProductoMaster().getNombreBase() : null;
            detalle.add(MovimientoDto.builder()
                    .idmov(m.getIdmov()).fecha(m.getFecha()).codti(m.getCodti()).tienda(nombresTienda.get(m.getCodti()))
                    .usuario(m.getUsuario() != null ? m.getUsuario().getNombreCompleto() : null)
                    .tipo(m.getTipo()).categoria(categoria(m.getTipo(), m.getMotivo()))
                    .articulo(nombre).codigo(m.getProducto() != null ? m.getProducto().getCodpro() : null).imei(m.getImei())
                    .cantidad(m.getCantidad()).valor(valor).motivo(m.getMotivo()).referencia(m.getReferencia())
                    .build());
        }
        return MermaReporteDto.builder()
                .desde(ini1).hasta(fin1).codti(tienda).conCostos(conCostos)
                .movimientos(detalle.size()).perdida(conCostos ? perdida : null).sobrante(conCostos ? sobrante : null)
                .porCategoria(resumir(detalle, MovimientoDto::getCategoria))
                .porUsuario(resumir(detalle, d -> d.getUsuario() != null ? d.getUsuario() : "Sin usuario"))
                .porTienda(resumir(detalle, d -> d.getTienda() != null ? d.getTienda() : "Tienda " + d.getCodti()))
                .detalle(detalle)
                .build();
    }

    private BigDecimal costoDe(MovimientoInventario m) {
        if (m.getImei() != null) {
            Optional<ProductoImei> u = imeiRepository.findByImei(m.getImei());
            if (u.isPresent() && u.get().getCosto() != null) return u.get().getCosto();
        }
        return m.getProducto() != null ? m.getProducto().getPreciopro() : null;
    }

    private List<ResumenDto> resumir(List<MovimientoDto> detalle, Function<MovimientoDto, String> clave) {
        Map<String, ResumenDto> m = new LinkedHashMap<>();
        for (MovimientoDto d : detalle) {
            ResumenDto r = m.computeIfAbsent(clave.apply(d), k -> ResumenDto.builder().nombre(k).movimientos(0).unidades(BigDecimal.ZERO)
                    .valor(d.getValor() == null ? null : BigDecimal.ZERO).build());
            r.setMovimientos(r.getMovimientos() + 1);
            r.setUnidades(r.getUnidades().add(d.getCantidad()));
            if (d.getValor() != null && r.getValor() != null) r.setValor(r.getValor().add(d.getValor()));
        }
        List<ResumenDto> l = new ArrayList<>(m.values());
        // primero lo que más pesa: la mayor pérdida (o, sin costos, las más unidades que salieron)
        l.sort(Comparator.comparing((ResumenDto r) -> r.getValor() != null ? r.getValor() : r.getUnidades()));
        return l;
    }

    /** Clasifica el movimiento por lo que dice su motivo (los ajustes del inventario físico llevan su propio texto). */
    static String categoria(String tipo, String motivo) {
        String t = motivo == null ? "" : Normalizer.normalize(motivo, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
        if (t.contains("inventario fisico")) return "Ajuste por inventario físico";
        if (t.contains("robo") || t.contains("extrav") || t.contains("perdi") || t.contains("perdida")) return "Robo o extravío";
        if (t.contains("merma") || t.contains("dano") || t.contains("descompus") || t.contains("roto") || t.contains("rota")) return "Daño o merma";
        if (t.contains("muestra") || t.contains("regalo") || t.contains("consumo")) return "Muestra, regalo o consumo";
        if (t.contains("transferencia") || t.contains("traspaso")) return "Salida por transferencia (fuera de traspasos)";
        if (t.contains("error") && t.contains("captura")) return "Error de captura";
        if (t.contains("ajuste")) return "Ajuste manual de inventario";
        return "Otro motivo";
    }
}
