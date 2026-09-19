package com.skycel.backend.config;

import com.skycel.backend.service.MotivoCajaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Siembra el catálogo base de motivos de caja al iniciar (solo crea los que falten). */
@Component
@RequiredArgsConstructor
public class MotivosCajaInitializer implements CommandLineRunner {

    private final MotivoCajaService motivoCajaService;

    @Override
    public void run(String... args) {
        motivoCajaService.asegurarMotivosBase();
    }
}
