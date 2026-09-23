package com.skycel.backend.service;

import com.skycel.backend.domain.entity.Caja;
import com.skycel.backend.domain.entity.Tienda;
import com.skycel.backend.dto.caja.CajaRequestDto;
import com.skycel.backend.dto.caja.CajaResponseDto;
import com.skycel.backend.repository.CajaRepository;
import com.skycel.backend.repository.TiendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CajaService {

    private final CajaRepository cajaRepository;
    private final TiendaRepository tiendaRepository;

    @Transactional(readOnly = true)
    public List<CajaResponseDto> listarPorTienda(Integer codti) {
        return cajaRepository.findByTienda_Codti(codti).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Da de alta una caja nueva. La primera de una tienda queda como principal automáticamente. */
    @Transactional
    public CajaResponseDto crear(CajaRequestDto dto) {
        Tienda tienda = tiendaRepository.findById(dto.getCodti())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no encontrada: " + dto.getCodti()));

        List<Caja> existentes = cajaRepository.findByTienda_Codti(dto.getCodti());
        boolean principal = dto.getEsCajaPrincipal() != null ? dto.getEsCajaPrincipal() : existentes.isEmpty();

        if (principal) {
            for (Caja c : existentes) {
                if (Boolean.TRUE.equals(c.getEsCajaPrincipal())) {
                    c.setEsCajaPrincipal(false);
                    cajaRepository.save(c);
                }
            }
        }

        Caja caja = Caja.builder()
                .nombreCaja(dto.getNombreCaja().trim())
                .tienda(tienda)
                .esCajaPrincipal(principal)
                .build();
        return toDto(cajaRepository.save(caja));
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
