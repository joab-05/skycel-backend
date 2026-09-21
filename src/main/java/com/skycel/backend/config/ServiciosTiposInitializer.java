package com.skycel.backend.config;

import com.skycel.backend.service.CorteServiciosService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Siembra los servicios base del corte diario (recargas, pagos de servicios, pines y PayJoy) si el catálogo está vacío. */
@Component
@RequiredArgsConstructor
public class ServiciosTiposInitializer implements CommandLineRunner {

    private final CorteServiciosService corteServiciosService;

    @Override
    public void run(String... args) {
        corteServiciosService.asegurarTiposBase();
    }
}
