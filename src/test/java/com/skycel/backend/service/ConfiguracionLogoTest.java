package com.skycel.backend.service;

import com.skycel.backend.domain.entity.ConfiguracionNegocio;
import com.skycel.backend.repository.ConfiguracionNegocioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Logo del negocio: se normaliza a PNG de hasta 512 px; lo que no es imagen se rechaza. */
@ExtendWith(MockitoExtension.class)
class ConfiguracionLogoTest {

    @Mock private ConfiguracionNegocioRepository repository;
    @InjectMocks private ConfiguracionNegocioService service;

    private ConfiguracionNegocio fila;

    @BeforeEach
    void setUp() {
        fila = ConfiguracionNegocio.builder().id(1).nombreComercial("Skycel").diasDevolucion(15).build();
        lenient().when(repository.findById(1)).thenReturn(Optional.of(fila));
        lenient().when(repository.save(any(ConfiguracionNegocio.class))).thenAnswer(i -> i.getArgument(0));
    }

    private byte[] imagen(int ancho, int alto, String formato) throws Exception {
        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, formato, out);
        return out.toByteArray();
    }

    @Test
    void una_imagen_chica_se_guarda_como_png_sin_cambiar_de_tamano() throws Exception {
        long version = service.guardarLogo(imagen(200, 100, "png"), "root");

        BufferedImage guardada = ImageIO.read(new ByteArrayInputStream(fila.getLogo()));
        assertThat(guardada.getWidth()).isEqualTo(200);
        assertThat(guardada.getHeight()).isEqualTo(100);
        assertThat(fila.getLogoVersion()).isEqualTo(version).isPositive();
        assertThat(fila.getActualizadoPor()).isEqualTo("root");
    }

    @Test
    void una_imagen_grande_se_reduce_a_512_px_conservando_la_proporcion() throws Exception {
        service.guardarLogo(imagen(2000, 1000, "jpg"), "root");

        BufferedImage guardada = ImageIO.read(new ByteArrayInputStream(fila.getLogo()));
        assertThat(guardada.getWidth()).isEqualTo(512);
        assertThat(guardada.getHeight()).isEqualTo(256);
    }

    @Test
    void lo_que_no_es_una_imagen_se_rechaza() {
        assertThatThrownBy(() -> service.guardarLogo("esto no es una imagen".getBytes(), "root"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(fila.getLogo()).isNull();
    }

    @Test
    void un_archivo_de_mas_de_2_mb_se_rechaza() {
        assertThatThrownBy(() -> service.guardarLogo(new byte[ConfiguracionNegocioService.LOGO_MAX_BYTES + 1], "root"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void sin_logo_no_hay_version_y_al_quitarlo_vuelve_a_no_haber() throws Exception {
        assertThat(service.logo()).isEmpty();
        assertThat(service.logoVersion()).isZero();

        service.guardarLogo(imagen(50, 50, "png"), "root");
        assertThat(service.logo()).isPresent();
        assertThat(service.logoVersion()).isPositive();

        service.quitarLogo("root");
        assertThat(service.logo()).isEmpty();
        assertThat(service.logoVersion()).isZero();
    }
}
