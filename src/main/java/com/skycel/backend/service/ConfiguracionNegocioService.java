package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ConfiguracionNegocio;
import com.skycel.backend.dto.configuracion.ConfiguracionNegocioDto;
import com.skycel.backend.repository.ConfiguracionNegocioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos del negocio compartidos por todas las tiendas. Si nunca se han capturado, se crean con valores por omisión. */
@Service
@RequiredArgsConstructor
public class ConfiguracionNegocioService {

    public static final int DIAS_DEVOLUCION_POR_OMISION = 15;

    private final ConfiguracionNegocioRepository repository;

    @Transactional
    public ConfiguracionNegocioDto obtener() {
        return toDto(cargar());
    }

    /** Días que una venta admite devoluciones. */
    @Transactional
    public int diasDevolucion() {
        Integer dias = cargar().getDiasDevolucion();
        return dias != null ? dias : DIAS_DEVOLUCION_POR_OMISION;
    }

    /** Guarda los datos del negocio (solo ROOT/ADMIN, lo exige el controlador). */
    @Transactional
    public ConfiguracionNegocioDto actualizar(ConfiguracionNegocioDto dto, String username) {
        ConfiguracionNegocio c = cargar();
        c.setNombreComercial(dto.getNombreComercial().trim());
        c.setRazonSocial(limpio(dto.getRazonSocial()));
        c.setRfc(dto.getRfc() == null || dto.getRfc().isBlank() ? null : dto.getRfc().trim().toUpperCase());
        c.setEmail(limpio(dto.getEmail()));
        c.setSitioWeb(limpio(dto.getSitioWeb()));
        c.setTicketPie(limpio(dto.getTicketPie()));
        c.setTicketLeyenda(limpio(dto.getTicketLeyenda()));
        c.setDiasDevolucion(dto.getDiasDevolucion());
        c.setActualizadoPor(username);
        return toDto(repository.save(c));
    }

    private ConfiguracionNegocio cargar() {
        return repository.findById(ConfiguracionNegocio.ID_UNICO).orElseGet(() -> repository.save(ConfiguracionNegocio.builder()
                .id(ConfiguracionNegocio.ID_UNICO).nombreComercial("Skycel Tecnologías")
                .ticketPie("¡Gracias por su compra!").diasDevolucion(DIAS_DEVOLUCION_POR_OMISION).build()));
    }

    private String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private ConfiguracionNegocioDto toDto(ConfiguracionNegocio c) {
        return ConfiguracionNegocioDto.builder()
                .nombreComercial(c.getNombreComercial()).razonSocial(c.getRazonSocial()).rfc(c.getRfc())
                .email(c.getEmail()).sitioWeb(c.getSitioWeb()).ticketPie(c.getTicketPie()).ticketLeyenda(c.getTicketLeyenda())
                .diasDevolucion(c.getDiasDevolucion() != null ? c.getDiasDevolucion() : DIAS_DEVOLUCION_POR_OMISION)
                .fechaActualizacion(c.getFechaActualizacion()).actualizadoPor(c.getActualizadoPor()).build();
    }
}
