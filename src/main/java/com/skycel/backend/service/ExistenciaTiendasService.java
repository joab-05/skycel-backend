package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.dto.producto.ExistenciaTiendaDto;
import com.skycel.backend.repository.ProductoImeiRepository;
import com.skycel.backend.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * La búsqueda entre sucursales: dónde hay existencia de un artículo, para pedirlo en un traspaso. Solo muestra lo que tiene
 * existencia y nunca costos, así que sirve a cualquier usuario autenticado (igual que consultar el inventario de otra sucursal).
 */
@Service
@RequiredArgsConstructor
public class ExistenciaTiendasService {

    static final int MAXIMO = 300;

    private final ProductoRepository productoRepository;
    private final ProductoImeiRepository imeiRepository;

    /**
     * @param texto        palabras del nombre, código de artículo o IMEI/serie (todas deben aparecer); mínimo 2 letras
     * @param excluirCodti sucursal que se omite (normalmente la de quien busca); null para no omitir ninguna
     */
    @Transactional(readOnly = true)
    public List<ExistenciaTiendaDto> buscar(String texto, Integer excluirCodti) {
        String q = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) return List.of();
        String[] palabras = q.split("\\s+");
        // la base busca por la palabra más larga (la más selectiva) y las demás se verifican aquí
        String ancla = Arrays.stream(palabras).max(Comparator.comparingInt(String::length)).orElse(q);
        String[] otras = Arrays.stream(palabras).filter(w -> !w.equals(ancla)).toArray(String[]::new);

        List<ExistenciaTiendaDto> lista = new ArrayList<>();
        for (Producto p : productoRepository.existenciasPorTexto(ancla, PageRequest.of(0, MAXIMO))) {
            if (p.getTienda().getCodti().equals(excluirCodti)) continue;
            String nombre = p.getProductoMaster().getNombreBase();
            if (!contieneTodas(otras, nombre + " " + p.getCodpro())) continue;
            lista.add(new ExistenciaTiendaDto(p.getTienda().getCodti(), p.getTienda().getNombre(), Boolean.TRUE.equals(p.getTienda().getEsAlmacen()),
                    false, p.getCodpro(), p.getProductoMaster().getIdprodmaster(), nombre, p.getStock(), p.getPreciopub()));
        }
        for (Object[] f : imeiRepository.existenciasEquipos(ancla)) {
            Integer codti = (Integer) f[2];
            if (codti.equals(excluirCodti)) continue;
            String nombre = (String) f[1];
            if (!contieneTodas(otras, nombre)) continue;
            lista.add(new ExistenciaTiendaDto(codti, (String) f[3], Boolean.TRUE.equals(f[4]), true, null, (Integer) f[0], nombre,
                    BigDecimal.valueOf(((Number) f[5]).longValue()), (BigDecimal) f[6]));
        }
        lista.sort(Comparator.comparing(ExistenciaTiendaDto::nombre, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ExistenciaTiendaDto::tienda, String.CASE_INSENSITIVE_ORDER));
        return lista.size() > MAXIMO ? lista.subList(0, MAXIMO) : lista;
    }

    private static boolean contieneTodas(String[] palabras, String texto) {
        String t = texto.toLowerCase(Locale.ROOT);
        for (String w : palabras) if (!t.contains(w)) return false;
        return true;
    }
}
