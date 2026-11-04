package com.skycel.backend.service;

import com.skycel.backend.domain.entity.EquipoJSystem;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.domain.entity.Usuario;
import com.skycel.backend.repository.EquipoJSystemRepository;
import com.skycel.backend.repository.UsuarioRepository;
import com.skycel.backend.service.EquipoJSystemService.Latido;
import com.skycel.backend.service.EquipoJSystemService.Situacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipoJSystemServiceTest {

    @Mock private EquipoJSystemRepository repository;
    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private EquipoJSystemService service;

    private EquipoJSystem equipo(String version, String pendiente, boolean error, LocalDateTime visto) {
        return EquipoJSystem.builder().host("PC-ZOCALO").version(version).versionPendiente(pendiente).conError(error).ultimaConexion(visto).build();
    }

    @Test
    @DisplayName("el equipo con la versión publicada está al día; con una anterior, desactualizado")
    void alDiaODesactualizada() {
        LocalDateTime ahora = LocalDateTime.now();
        assertThat(EquipoJSystemService.situacion(equipo("202611031830", null, false, ahora), "202611031830", ahora)).isEqualTo(Situacion.AL_DIA);
        assertThat(EquipoJSystemService.situacion(equipo("202611021000", null, false, ahora), "202611031830", ahora)).isEqualTo(Situacion.DESACTUALIZADA);
    }

    @Test
    @DisplayName("ya bajó la nueva y espera que cierren el programa; si falló la actualización, con error; si no reporta en un día, sin reportar")
    void esperaReinicioErrorYSinReportar() {
        LocalDateTime ahora = LocalDateTime.now();
        assertThat(EquipoJSystemService.situacion(equipo("202611021000", "202611031830", false, ahora), "202611031830", ahora)).isEqualTo(Situacion.ESPERA_REINICIO);
        assertThat(EquipoJSystemService.situacion(equipo("202611021000", null, true, ahora), "202611031830", ahora)).isEqualTo(Situacion.CON_ERROR);
        assertThat(EquipoJSystemService.situacion(equipo("202611021000", null, false, ahora.minusHours(25)), "202611031830", ahora)).isEqualTo(Situacion.SIN_REPORTAR);
        // sin versión publicada no hay con qué comparar
        assertThat(EquipoJSystemService.situacion(equipo("1", null, false, ahora), null, ahora)).isEqualTo(Situacion.AL_DIA);
    }

    @Test
    @DisplayName("el reporte crea o actualiza la fila de la computadora y anota quién la usa y en qué tienda")
    void registraElLatido() {
        Tienda zocalo = new Tienda();
        zocalo.setCodti(2);
        zocalo.setNombre("Zócalo");
        Usuario abigail = new Usuario();
        abigail.setUsername("abigail");
        abigail.setNombreCompleto("Abigail");
        abigail.setTienda(zocalo);
        when(repository.findByHost("PC-ZOCALO")).thenReturn(Optional.empty());
        when(usuarioRepository.findByUsername("abigail")).thenReturn(Optional.of(abigail));

        service.registrar(new Latido(" PC-ZOCALO ", "Caja Zócalo", "202611031830", null, "Este equipo ya tiene la versión más nueva", false), "abigail");

        ArgumentCaptor<EquipoJSystem> c = ArgumentCaptor.forClass(EquipoJSystem.class);
        verify(repository).save(c.capture());
        assertThat(c.getValue().getHost()).isEqualTo("PC-ZOCALO");
        assertThat(c.getValue().getNombreEquipo()).isEqualTo("Caja Zócalo");
        assertThat(c.getValue().getNombreTienda()).isEqualTo("Zócalo");
        assertThat(c.getValue().getUsuario()).isEqualTo("Abigail");
        assertThat(c.getValue().getConError()).isFalse();
    }

    @Test
    @DisplayName("sin nombre de computadora no se guarda nada")
    void sinHostNoGuarda() {
        service.registrar(new Latido("  ", null, "1", null, null, false), "abigail");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("la lista toma la versión publicada de la carpeta del servidor")
    void listaConLaVersionPublicada(@TempDir Path carpeta) throws Exception {
        Files.writeString(carpeta.resolve("version.txt"), "202611031830\n");
        ReflectionTestUtils.setField(service, "carpetaPublicada", carpeta.toString());
        when(repository.findAllByOrderByUltimaConexionDesc()).thenReturn(List.of(equipo("202611021000", null, false, LocalDateTime.now())));

        var lista = service.listar();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).situacion()).isEqualTo(Situacion.DESACTUALIZADA);
    }
}
