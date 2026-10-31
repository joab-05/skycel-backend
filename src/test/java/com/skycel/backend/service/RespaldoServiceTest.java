package com.skycel.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Qué copias del respaldo se quedan y cómo se sabe que una quedó completa. */
class RespaldoServiceTest {

    private static Path copia(Path carpeta, String fecha) throws IOException {
        return Files.writeString(carpeta.resolve("skyceldb2-" + fecha + "-0230.sql.gz"), "x");
    }

    @Test
    @DisplayName("rotar: se quedan las últimas copias diarias y la más nueva de cada mes reciente; lo demás se borra")
    void rotar(@TempDir Path dir) throws IOException {
        List<Path> todas = new ArrayList<>();
        // 40 días seguidos hacia atrás desde el 2026-10-31
        LocalDate hoy = LocalDate.of(2026, 10, 31);
        for (int i = 0; i < 40; i++) todas.add(copia(dir, hoy.minusDays(i).toString().replace("-", "")));
        copia(dir, "20260115");   // enero: más viejo que 6 meses → se borra
        copia(dir, "20260520");   // mayo: la única de su mes y dentro de 6 meses → se queda
        Files.writeString(dir.resolve("notas.txt"), "no es una copia");

        RespaldoService.rotar(dir, hoy, 14, 6);

        assertThat(Files.exists(dir.resolve("skyceldb2-20261031-0230.sql.gz"))).isTrue();          // la de hoy
        assertThat(Files.exists(dir.resolve("skyceldb2-20261018-0230.sql.gz"))).isTrue();          // la 14.ª
        assertThat(Files.exists(dir.resolve("skyceldb2-20261017-0230.sql.gz"))).isFalse();         // la 15.ª ya no
        assertThat(Files.exists(dir.resolve("skyceldb2-20260930-0230.sql.gz"))).isTrue();          // la más nueva de septiembre
        assertThat(Files.exists(dir.resolve("skyceldb2-20260929-0230.sql.gz"))).isFalse();
        assertThat(Files.exists(dir.resolve("skyceldb2-20260520-0230.sql.gz"))).isTrue();          // mensual
        assertThat(Files.exists(dir.resolve("skyceldb2-20260115-0230.sql.gz"))).isFalse();         // muy vieja
        assertThat(Files.exists(dir.resolve("notas.txt"))).isTrue();                               // no se toca lo que no es copia
    }

    @Test
    @DisplayName("quedan: nunca borra la copia más reciente aunque sea la única")
    void unaSola(@TempDir Path dir) throws IOException {
        Path unica = copia(dir, "20260101");
        Set<Path> quedan = RespaldoService.quedan(List.of(unica), LocalDate.of(2026, 10, 31), 1, 0);
        assertThat(quedan).containsExactly(unica);
    }

    @Test
    @DisplayName("terminoBien: solo un volcado con el cierre de mysqldump cuenta como completo")
    void terminoBien(@TempDir Path dir) throws IOException {
        Path bueno = Files.writeString(dir.resolve("a.sql"), "-- MySQL dump\n".repeat(20) + "-- Dump completed on 2026-10-31  2:30:01\n");
        Path cortado = Files.writeString(dir.resolve("b.sql"), "-- MySQL dump\n".repeat(20) + "INSERT INTO t VALUES (1");
        assertThat(RespaldoService.terminoBien(bueno)).isTrue();
        assertThat(RespaldoService.terminoBien(cortado)).isFalse();
    }
}
