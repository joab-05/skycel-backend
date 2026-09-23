package com.skycel.backend.service;

import com.skycel.backend.domain.dto.request.ProveedorRequestDTO;
import com.skycel.backend.domain.dto.response.ProveedorResponseDTO;
import com.skycel.backend.domain.entity.Proveedor;
import com.skycel.backend.domain.mapper.CatalogoMapper;
import com.skycel.backend.repository.ProveedorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock private ProveedorRepository proveedorRepository;
    @Mock private CatalogoMapper catalogoMapper;

    @InjectMocks
    private CatalogoService service;

    private void crear(String nombreFiscal) {
        ProveedorRequestDTO dto = new ProveedorRequestDTO();
        dto.setNombreCorto("Case Phone");
        dto.setNombreFiscal(nombreFiscal);
        Proveedor entidad = Proveedor.builder().nombreCorto("Case Phone").nombreFiscal(nombreFiscal).build();
        when(catalogoMapper.toProveedorEntity(dto)).thenReturn(entidad);
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(catalogoMapper.toProveedorResponse(any(Proveedor.class))).thenReturn(new ProveedorResponseDTO());
        service.crearProveedor(dto);
    }

    @Test
    @DisplayName("crearProveedor: sin razón social se usa el nombre corto (la columna es obligatoria)")
    void sinRazonSocial_usaNombreCorto() {
        crear(null);
        ArgumentCaptor<Proveedor> cap = ArgumentCaptor.forClass(Proveedor.class);
        verify(proveedorRepository).save(cap.capture());
        assertThat(cap.getValue().getNombreFiscal()).isEqualTo("Case Phone");
    }

    @Test
    @DisplayName("crearProveedor: con razón social se respeta")
    void conRazonSocial_seRespeta() {
        crear("Case Phone SA de CV");
        ArgumentCaptor<Proveedor> cap = ArgumentCaptor.forClass(Proveedor.class);
        verify(proveedorRepository).save(cap.capture());
        assertThat(cap.getValue().getNombreFiscal()).isEqualTo("Case Phone SA de CV");
    }
}
