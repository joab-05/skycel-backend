package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoImei;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.TipoProducto;
import com.skycel.backend.repository.ProductoImeiRepository;
import com.skycel.backend.repository.ProductoMasterRepository;
import com.skycel.backend.repository.ProductoRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

/**
 * La vista de Precios de los administradores: todos los artículos con su precio actual (el más bajo y el más alto entre
 * las sucursales) y un solo lugar para cambiar el precio de varios a la vez, en todas las sucursales y la bodega.
 */
@Service
@RequiredArgsConstructor
public class PrecioService {

    private final ProductoMasterRepository masterRepository;
    private final ProductoRepository productoRepository;
    private final ProductoImeiRepository imeiRepository;
    private final UsuarioRepository usuarioRepository;
    private final PermisoService permisoService;

    /** Un artículo con su precio actual. {@code costoMaximo} solo viene si quien consulta puede ver costos. */
    public record Articulo(Integer idprodmaster, String nombre, String tipo, String categoria, BigDecimal precioMinimo,
                           BigDecimal precioMaximo, BigDecimal costoMaximo, long unidades) {}

    /** El precio nuevo de un artículo. */
    public record Cambio(Integer idprodmaster, BigDecimal precioVenta) {}

    public record Resultado(int articulos, int registros) {}

    @Transactional(readOnly = true)
    public List<Articulo> listar(String texto, String tipo) {
        boolean verCostos = permisoService.tiene("COSTOS_VER");
        Map<Integer, Object[]> resumen = new HashMap<>();
        for (Object[] f : productoRepository.resumenDePrecios()) resumen.put((Integer) f[0], f);
        for (Object[] f : imeiRepository.resumenDePreciosEquipos()) resumen.put((Integer) f[0], f);
        String buscado = normalizar(texto);
        List<Articulo> lista = new ArrayList<>();
        for (ProductoMaster m : masterRepository.findByActivoTrue()) {
            if (tipo != null && !tipo.isBlank() && !m.getTipo().name().equalsIgnoreCase(tipo)) continue;
            String nombre = m.getNombreBase() != null ? m.getNombreBase() : "";
            String categoria = m.getCategoria() != null ? m.getCategoria().getNombre() : "";
            if (!buscado.isEmpty() && !(normalizar(nombre) + " " + normalizar(categoria)).contains(buscado)) continue;
            Object[] f = resumen.get(m.getIdprodmaster());
            lista.add(new Articulo(m.getIdprodmaster(), nombre, m.getTipo().name(), categoria,
                    f == null ? null : (BigDecimal) f[1], f == null ? null : (BigDecimal) f[2],
                    verCostos && f != null ? (BigDecimal) f[3] : null, f == null ? 0 : (Long) f[4]));
        }
        lista.sort(Comparator.comparing(Articulo::nombre, String.CASE_INSENSITIVE_ORDER));
        return lista;
    }

    /** Pone el precio de cada artículo en todas las sucursales y la bodega: las existencias y las unidades disponibles. */
    @Transactional
    public Resultado aplicar(List<Cambio> cambios, String username) {
        if (cambios == null || cambios.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay precios que cambiar.");
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        int registros = 0;
        for (Cambio c : cambios) {
            if (c.idprodmaster() == null || c.precioVenta() == null || c.precioVenta().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada precio debe ser mayor a $0.");
            }
            ProductoMaster m = masterRepository.findById(c.idprodmaster())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado: " + c.idprodmaster()));
            if (m.getTipo() == TipoProducto.CELULAR || m.getTipo() == TipoProducto.TABLET) {
                List<ProductoImei> unidades = imeiRepository.findByProductoMaster_IdprodmasterAndEstado(m.getIdprodmaster(), ProductoImei.DISPONIBLE);
                unidades.forEach(u -> u.setPrecioVenta(c.precioVenta()));
                imeiRepository.saveAll(unidades);
                registros += unidades.size();
            } else {
                List<Producto> productos = productoRepository.findByProductoMaster_IdprodmasterOrderByIdproductoAsc(m.getIdprodmaster());
                for (Producto p : productos) {
                    p.setPreciopub(c.precioVenta());
                    p.setFechaModificacion(ahora);
                    if (usuario != null) p.setUsuarioModificador(usuario);
                }
                productoRepository.saveAll(productos);
                registros += productos.size();
            }
        }
        return new Resultado(cambios.size(), registros);
    }

    private static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
