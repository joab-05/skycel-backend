package com.skycel.backend.config;

import com.skycel.backend.service.PermisoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Al iniciar, siembra los permisos que falten con los roles que ya los tenían (ver {@code Permiso}). */
@Component
@RequiredArgsConstructor
public class PermisosInitializer implements CommandLineRunner {

    private final PermisoService permisoService;

    @Override
    public void run(String... args) {
        permisoService.sembrar();
    }
}
