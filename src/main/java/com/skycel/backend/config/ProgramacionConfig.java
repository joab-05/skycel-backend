package com.skycel.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enciende las tareas programadas (@Scheduled): el respaldo diario y el marcado de cuentas y compras vencidas. */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
