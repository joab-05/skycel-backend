package com.skycel.backend.service;

import com.skycel.backend.domain.entity.CustomRevisionEntity;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Historial de cambios de un registro, a partir de lo que Hibernate Envers ya guarda solo para varias
 * entidades (ver {@code @Audited} en cada una). No es un log de auditoría de propósito general: solo
 * expone las entidades registradas en {@link #ENTIDADES}, y solo sus campos simples (precio, nombre,
 * stock, rol...) — nunca contraseñas ni relaciones, para no arriesgar reventar por referencias fuera de
 * la sesión ni exponer datos sensibles.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final EntityManager entityManager;

    private static final Map<String, Class<?>> ENTIDADES = Map.of(
            "producto", Producto.class,
            "producto-master", ProductoMaster.class,
            "usuario", Usuario.class,
            "tienda", Tienda.class,
            "descuento", com.skycel.backend.domain.entity.DescuentoRegla.class
    );

    /** Campos que nunca se muestran, aunque sean simples (por nombre exacto, en cualquier entidad). */
    private static final Set<String> CAMPOS_OCULTOS = Set.of("password", "version");

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historial(String entidad, Integer id) {
        Class<?> tipo = ENTIDADES.get(entidad);
        if (tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de registro no reconocido: '" + entidad + "'. Válidos: " + String.join(", ", ENTIDADES.keySet()));
        }

        AuditReader reader = AuditReaderFactory.get(entityManager);
        @SuppressWarnings("unchecked")
        List<Object[]> filas = reader.createQuery()
                .forRevisionsOfEntity(tipo, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        if (filas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay historial para ese registro.");
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        Object anterior = null;
        for (Object[] fila : filas) {
            Object snapshot = fila[0];
            CustomRevisionEntity revision = (CustomRevisionEntity) fila[1];
            RevisionType tipoRevision = (RevisionType) fila[2];

            // Si esta entidad se empezó a auditar cuando ya tenía filas (como ProductoMaster, agregado después),
            // su primer cambio detectado llega como MOD sin ninguna revisión previa con la que compararlo.
            boolean sinAntecedente = tipoRevision == RevisionType.MOD && anterior == null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("revision", revision.getRev());
            item.put("fecha", revision.getFecha());
            item.put("usuario", revision.getUsername() != null ? revision.getUsername() : "—");
            item.put("tipo", tipoDisplay(tipoRevision));
            item.put("cambios", tipoRevision == RevisionType.MOD ? diferencias(anterior, snapshot, tipo) : List.of());
            item.put("sinAntecedente", sinAntecedente);
            resultado.add(item);

            if (snapshot != null) anterior = snapshot;
        }
        return resultado;
    }

    private String tipoDisplay(RevisionType t) {
        return switch (t) {
            case ADD -> "Creado";
            case DEL -> "Eliminado";
            default -> "Modificado";
        };
    }

    /**
     * Compara los campos simples de dos capturas del mismo registro y devuelve los que cambiaron.
     * Deliberadamente NO toca relaciones (@ManyToOne/@OneToMany/...) para no arriesgar cargar algo fuera
     * de sesión, ni fechas de auditoría (ya las cuenta la revisión), ni contraseñas ni el campo de
     * bloqueo optimista. Paquete-visible para poder probarla sin Spring ni base de datos.
     */
    static List<Map<String, Object>> diferencias(Object anterior, Object actual, Class<?> tipo) {
        if (anterior == null || actual == null) return List.of();
        List<Map<String, Object>> cambios = new ArrayList<>();
        for (Field campo : tipo.getDeclaredFields()) {
            if (esCampoOmitido(campo)) continue;
            campo.setAccessible(true);
            Object antes, despues;
            try {
                antes = campo.get(anterior);
                despues = campo.get(actual);
            } catch (IllegalAccessException e) {
                continue; // no debería pasar tras setAccessible, pero si pasa, simplemente se omite ese campo
            }
            if (sonIguales(antes, despues)) continue;
            Map<String, Object> cambio = new LinkedHashMap<>();
            cambio.put("campo", campo.getName());
            cambio.put("antes", antes);
            cambio.put("despues", despues);
            cambios.add(cambio);
        }
        return cambios;
    }

    private static boolean sonIguales(Object a, Object b) {
        if (a instanceof BigDecimal ba && b instanceof BigDecimal bb) return ba.compareTo(bb) == 0;
        return Objects.equals(a, b);
    }

    private static boolean esCampoOmitido(Field campo) {
        if (Modifier.isStatic(campo.getModifiers())) return true;
        if (CAMPOS_OCULTOS.contains(campo.getName())) return true;
        for (Class<? extends java.lang.annotation.Annotation> relacion : List.of(
                jakarta.persistence.ManyToOne.class, jakarta.persistence.OneToMany.class,
                jakarta.persistence.ManyToMany.class, jakarta.persistence.OneToOne.class)) {
            if (campo.isAnnotationPresent(relacion)) return true;
        }
        Class<?> t = campo.getType();
        return LocalDateTime.class.isAssignableFrom(t) || LocalDate.class.isAssignableFrom(t);
    }
}
