package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ConfiguracionNegocio;
import com.skycel.backend.dto.configuracion.ConfiguracionNegocioDto;
import com.skycel.backend.repository.ConfiguracionNegocioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Datos del negocio (sin Spring ni base de datos). */
@ExtendWith(MockitoExtension.class)
class ConfiguracionNegocioServiceTest {

    @Mock private ConfiguracionNegocioRepository repository;

    @InjectMocks
    private ConfiguracionNegocioService service;

    @Test
    @DisplayName("si nunca se han capturado, se crean con los valores por omisión (15 días de devolución)")
    void valoresPorOmision() {
        when(repository.findById(1)).thenReturn(Optional.empty());
        when(repository.save(any(ConfiguracionNegocio.class))).thenAnswer(i -> i.getArgument(0));

        ConfiguracionNegocioDto dto = service.obtener();

        assertThat(dto.getNombreComercial()).isEqualTo("Skycel Tecnologías");
        assertThat(dto.getDiasDevolucion()).isEqualTo(15);
        assertThat(service.diasDevolucion()).isEqualTo(15);
    }

    @Test
    @DisplayName("actualizar limpia los textos, pone el RFC en mayúsculas y deja vacío lo que viene en blanco")
    void actualizar() {
        ConfiguracionNegocio actual = ConfiguracionNegocio.builder().id(1).nombreComercial("Viejo").diasDevolucion(15).build();
        when(repository.findById(1)).thenReturn(Optional.of(actual));
        when(repository.save(any(ConfiguracionNegocio.class))).thenAnswer(i -> i.getArgument(0));

        ConfiguracionNegocioDto in = ConfiguracionNegocioDto.builder().nombreComercial("  Skycel  ").razonSocial("Skycel SA de CV")
                .rfc("sky200101ab1").email("  ").ticketPie("Gracias").diasDevolucion(30).build();
        ConfiguracionNegocioDto out = service.actualizar(in, "root");

        assertThat(out.getNombreComercial()).isEqualTo("Skycel");
        assertThat(out.getRfc()).isEqualTo("SKY200101AB1");
        assertThat(out.getEmail()).isNull();
        assertThat(out.getDiasDevolucion()).isEqualTo(30);
        assertThat(actual.getActualizadoPor()).isEqualTo("root");
        assertThat(service.diasDevolucion()).isEqualTo(30);
    }
}
