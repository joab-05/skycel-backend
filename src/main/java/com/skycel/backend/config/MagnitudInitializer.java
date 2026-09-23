package com.skycel.backend.config;

import com.skycel.backend.service.CatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Siembra la magnitud base ("Pieza") al iniciar si no existe ninguna — mismo patrón que MotivosCajaInitializer. */
@Component
@RequiredArgsConstructor
public class MagnitudInitializer implements CommandLineRunner {

    private final CatalogoService catalogoService;

    @Override
    public void run(String... args) {
        catalogoService.asegurarMagnitudBase();
    }
}
