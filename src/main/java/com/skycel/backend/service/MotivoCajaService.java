package com.skycel.backend.service;

import com.skycel.backend.domain.entity.CatMotivo;
import com.skycel.backend.dto.caja.MotivoCajaRequestDto;
import com.skycel.backend.dto.caja.MotivoCajaResponseDto;
import com.skycel.backend.repository.CatMotivoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de motivos de movimientos de caja (cat_motivo).
 * Los motivos base se siembran solos al iniciar; se pueden agregar más motivos desde la API
 * sin tocar código.
 */
@Service
@RequiredArgsConstructor
public class MotivoCajaService {

    // tipo_mov
    public static final byte ENTRADA = 1;
    public static final byte SALIDA  = 2;

    // cat_sat (clasificación contable)
    public static final byte CAT_OPERATIVO = 1;
    public static final byte CAT_NOMINA    = 2;
    public static final byte CAT_FISCAL    = 3;
    public static final byte CAT_TRASLADO  = 4;
    public static final byte CAT_PERSONAL  = 5;

    /** Motivos que el sistema registra solo; no se pueden capturar a mano ni desactivar. */
    public static final String VENTA             = "Venta";
    public static final String CANCELACION_VENTA = "Cancelación de venta";
    public static final String ABONO_CUENTA      = "Abono de cuenta por cobrar";
    public static final String ANTICIPO_SERVICIO = "Anticipo de servicio";
    public static final String DEVOLUCION_ANTICIPO = "Devolución de anticipo";
    public static final String DEVOLUCION_VENTA = "Devolución de venta";

    private static final Set<String> DEL_SISTEMA =
            Set.of(VENTA, CANCELACION_VENTA, ABONO_CUENTA, ANTICIPO_SERVICIO, DEVOLUCION_ANTICIPO, DEVOLUCION_VENTA);

    private record MotivoBase(String nombre, byte tipoMov, byte catSat) {}

    private static final List<MotivoBase> MOTIVOS_BASE = List.of(
            new MotivoBase(VENTA,                ENTRADA, CAT_OPERATIVO),
            new MotivoBase(CANCELACION_VENTA,    SALIDA,  CAT_OPERATIVO),
            new MotivoBase(ABONO_CUENTA,         ENTRADA, CAT_OPERATIVO),
            new MotivoBase(ANTICIPO_SERVICIO,    ENTRADA, CAT_OPERATIVO),
            new MotivoBase(DEVOLUCION_ANTICIPO,  SALIDA,  CAT_OPERATIVO),
            new MotivoBase(DEVOLUCION_VENTA,     SALIDA,  CAT_OPERATIVO),
            new MotivoBase("Fondo inicial",     ENTRADA, CAT_OPERATIVO),
            new MotivoBase("Otro ingreso",       ENTRADA, CAT_OPERATIVO),
            new MotivoBase("Gasto operativo",    SALIDA,  CAT_OPERATIVO),
            new MotivoBase("Pago de nómina",     SALIDA,  CAT_NOMINA),
            new MotivoBase("Traslado a bodega",  SALIDA,  CAT_TRASLADO),
            new MotivoBase("Retiro de dueño",    SALIDA,  CAT_PERSONAL)
    );

    private final CatMotivoRepository catMotivoRepository;

    public static boolean esDelSistema(String nombre) {
        return DEL_SISTEMA.contains(nombre);
    }

    /** Crea los motivos base que falten. Idempotente: no toca los que ya existen. */
    @Transactional
    public void asegurarMotivosBase() {
        for (MotivoBase base : MOTIVOS_BASE) {
            if (catMotivoRepository.findByNombre(base.nombre()).isEmpty()) {
                catMotivoRepository.save(CatMotivo.builder()
                        .nombre(base.nombre())
                        .tipoMov(base.tipoMov())
                        .catSat(base.catSat())
                        .activo(true)
                        .build());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<MotivoCajaResponseDto> listar(boolean soloActivos) {
        List<CatMotivo> motivos = soloActivos ? catMotivoRepository.findByActivoTrue()
                                              : catMotivoRepository.findAll();
        return motivos.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public MotivoCajaResponseDto crear(MotivoCajaRequestDto dto) {
        String nombre = dto.getNombre().trim();
        if (catMotivoRepository.findByNombre(nombre).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un motivo llamado '" + nombre + "'.");
        }
        CatMotivo motivo = catMotivoRepository.save(CatMotivo.builder()
                .nombre(nombre)
                .tipoMov(dto.getTipoMov())
                .catSat(dto.getCatSat())
                .activo(true)
                .build());
        return toDto(motivo);
    }

    @Transactional
    public MotivoCajaResponseDto cambiarActivo(Integer idmotivo, boolean activo) {
        CatMotivo motivo = catMotivoRepository.findById(idmotivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motivo no encontrado: " + idmotivo));
        if (!activo && esDelSistema(motivo.getNombre())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El motivo '" + motivo.getNombre() + "' lo usa el sistema y no se puede desactivar.");
        }
        motivo.setActivo(activo);
        return toDto(catMotivoRepository.save(motivo));
    }

    private MotivoCajaResponseDto toDto(CatMotivo m) {
        return MotivoCajaResponseDto.builder()
                .idmotivo(m.getIdmotivo())
                .nombre(m.getNombre())
                .tipoMov(m.getTipoMov())
                .tipoMovDisplay(m.getTipoMov() != null && m.getTipoMov() == ENTRADA ? "Entrada" : "Salida")
                .catSat(m.getCatSat())
                .catSatDisplay(catSatDisplay(m.getCatSat()))
                .activo(m.getActivo())
                .delSistema(esDelSistema(m.getNombre()))
                .build();
    }

    private String catSatDisplay(Byte catSat) {
        if (catSat == null) return "—";
        return switch (catSat) {
            case CAT_OPERATIVO -> "Operativo";
            case CAT_NOMINA    -> "Nómina";
            case CAT_FISCAL    -> "Fiscal";
            case CAT_TRASLADO  -> "Traslado";
            case CAT_PERSONAL  -> "Personal";
            default            -> "Otro";
        };
    }
}
