package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Caja;
import com.skycel.backend.dto.caja.CajaResponseDto;
import com.skycel.backend.repository.CajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CajaService {

    private final CajaRepository cajaRepository;

    @Transactional(readOnly = true)
    public List<CajaResponseDto> listarPorTienda(Integer codti) {
        return cajaRepository.findByTienda_Codti(codti).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private CajaResponseDto toDto(Caja c) {
        return CajaResponseDto.builder()
                .idCaja(c.getIdCaja())
                .nombreCaja(c.getNombreCaja())
                .codti(c.getTienda().getCodti())
                .nombreTienda(c.getTienda().getNombre())
                .esCajaPrincipal(c.getEsCajaPrincipal())
                .build();
    }
}
