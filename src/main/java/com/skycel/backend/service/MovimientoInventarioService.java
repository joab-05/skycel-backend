package com.skycel.backend.service;

import com.skycel.backend.domain.entity.MovimientoInventario;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.domain.enums.Rol;
import com.skycel.backend.dto.producto.MovimientoInventarioResponseDto;
import com.skycel.backend.repository.MovimientoInventarioRepository;
import com.skycel.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Historial de cambios de stock. Cada operación que mueve el stock lo avisa aquí después de aplicarlo. */
@Service
@RequiredArgsConstructor
public class MovimientoInventarioService {

    private final MovimientoInventarioRepository repository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Deja constancia de un cambio de stock. {@code stockAntes} es el stock previo; el actual se toma del producto
     * (ya modificado). Si no hubo cambio no registra nada.
     */
    @Transactional
    public void registrar(Producto p, BigDecimal stockAntes, String tipo, String motivo, String referencia) {
        BigDecimal antes = stockAntes != null ? stockAntes : BigDecimal.ZERO;
        BigDecimal despues = p.getStock() != null ? p.getStock() : BigDecimal.ZERO;
        BigDecimal delta = despues.subtract(antes);
        if (delta.signum() == 0) return;
        repository.save(MovimientoInventario.builder()
                .producto(p).tipo(tipo).cantidad(delta).stockAntes(antes).stockDespues(despues)
                .motivo(motivo == null || motivo.isBlank() ? null : recortar(motivo.trim(), 255))
                .referencia(referencia)
                .usuario(usuarioActual())
                .build());
    }

    /** Historial de una tienda. ROOT/ADMIN cualquiera; un encargado, solo la suya. Por defecto, los últimos 30 días. */
    @Transactional(readOnly = true)
    public List<MovimientoInventarioResponseDto> listar(Integer codti, LocalDateTime desde, LocalDateTime hasta,
                                                        String codpro, String username) {
        Usuario u = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario autenticado no encontrado"));
        boolean admin = u.getRol() == Rol.ROOT || u.getRol() == Rol.ADMIN;
        if (!admin && (u.getTienda() == null || !u.getTienda().getCodti().equals(codti))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puede consultar el inventario de su tienda.");
        }
        LocalDateTime fin = hasta != null ? hasta : LocalDateTime.now();
        LocalDateTime ini = desde != null ? desde : fin.minusDays(30);
        String filtro = codpro == null || codpro.isBlank() ? null : codpro.trim();
        return repository.buscar(codti, ini, fin, filtro).stream().map(this::toDto).collect(Collectors.toList());
    }

    private MovimientoInventarioResponseDto toDto(MovimientoInventario m) {
        Producto p = m.getProducto();
        return MovimientoInventarioResponseDto.builder()
                .idmov(m.getIdmov()).fecha(m.getFecha())
                .codpro(p.getCodpro()).nombreProducto(p.getProductoMaster().getNombreBase())
                .tipo(m.getTipo()).tipoDisplay(display(m.getTipo()))
                .cantidad(m.getCantidad()).stockAntes(m.getStockAntes()).stockDespues(m.getStockDespues())
                .motivo(m.getMotivo()).referencia(m.getReferencia())
                .usuario(m.getUsuario() != null ? m.getUsuario().getNombreCompleto() : null)
                .build();
    }

    private String display(String tipo) {
        return switch (tipo) {
            case "ALTA"              -> "Alta de producto";
            case "ENTRADA"           -> "Entrada";
            case "SALIDA"            -> "Salida";
            case "AJUSTE"            -> "Ajuste";
            case "VENTA"             -> "Venta";
            case "CANCELACION_VENTA" -> "Venta cancelada";
            case "TRASPASO_SALIDA"   -> "Traspaso enviado";
            case "TRASPASO_ENTRADA"  -> "Traspaso recibido";
            case "TRASPASO_ANULADO"  -> "Traspaso anulado";
            case "TRASPASO_FALTANTE" -> "Faltante de traspaso";
            case "DEVOLUCION"        -> "Devolución de venta";
            default                  -> tipo;
        };
    }

    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) return null;
        return usuarioRepository.findByUsername(auth.getName()).orElse(null);
    }

    private String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
