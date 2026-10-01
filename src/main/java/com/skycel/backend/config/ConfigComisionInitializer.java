package com.skycel.backend.config;

import com.skycel.backend.domain.entity.ConfigComision;
import com.skycel.backend.repository.ConfigComisionRepository;
import com.skycel.backend.service.ComisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Siembra la fila única de tasas de comisión al iniciar si no existe — mismo patrón que MagnitudInitializer.
 * Evita que dos peticiones concurrentes (la pantalla de Comisiones pide encargados y PayJoy a la vez) intenten
 * crearla al mismo tiempo y choquen por la llave primaria duplicada.
 */
@Component
@RequiredArgsConstructor
public class ConfigComisionInitializer implements CommandLineRunner {

    private final ConfigComisionRepository repository;

    @Override
    public void run(String... args) {
        if (repository.existsById(ConfigComision.ID_UNICO)) return;
        repository.save(ConfigComision.builder()
                .id(ConfigComision.ID_UNICO)
                .tasaEncargadoMensual(ComisionService.TASA_ENCARGADO_DEFECTO)
                .payjoyUmbral(ComisionService.PAYJOY_UMBRAL_DEFECTO)
                .payjoyTasaBaja(ComisionService.PAYJOY_TASA_BAJA_DEFECTO)
                .payjoyTasaAlta(ComisionService.PAYJOY_TASA_ALTA_DEFECTO)
                .build());
    }
}
