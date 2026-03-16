package com.skycel.backend.domain.mapper;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.request.CatalogoSimpleRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.dto.response.CatalogoSimpleResponseDTO;
import com.skycel.backend.domain.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CatalogoMapper {

    // --- Categoria ---
    @Mapping(target = "idcat", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "categoriaSuperior", ignore = true) // Set manually in service
    @Mapping(target = "subcategorias", ignore = true)
    Categoria toEntity(CategoriaRequestDTO dto);

    @Mapping(target = "idcat", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "categoriaSuperior", ignore = true)
    @Mapping(target = "subcategorias", ignore = true)
    void updateEntityFromDto(CategoriaRequestDTO dto, @MappingTarget Categoria entity);


    // --- Color ---
    @Mapping(target = "idcolor", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "nombreCol", source = "nombre")
    Color toColorEntity(CatalogoSimpleRequestDTO dto);

    @Mapping(target = "idcolor", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "nombreCol", source = "nombre")
    void updateColorFromDto(CatalogoSimpleRequestDTO dto, @MappingTarget Color entity);


    // --- Seccion ---
    @Mapping(target = "idseccion", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "nombreSec", source = "nombre")
    Seccion toSeccionEntity(CatalogoSimpleRequestDTO dto);

    @Mapping(target = "idseccion", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "nombreSec", source = "nombre")
    void updateSeccionFromDto(CatalogoSimpleRequestDTO dto, @MappingTarget Seccion entity);


    // --- Proveedor ---
    @Mapping(target = "idproveedor", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "nombreProv", source = "nombre")
    Proveedor toProveedorEntity(CatalogoSimpleRequestDTO dto);

    @Mapping(target = "idproveedor", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "nombreProv", source = "nombre")
    void updateProveedorFromDto(CatalogoSimpleRequestDTO dto, @MappingTarget Proveedor entity);


    // --- Magnitud ---
    @Mapping(target = "idmagnitud", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "nombreMag", source = "nombre")
    Magnitud toMagnitudEntity(CatalogoSimpleRequestDTO dto);

    @Mapping(target = "idmagnitud", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "nombreMag", source = "nombre")
    void updateMagnitudFromDto(CatalogoSimpleRequestDTO dto, @MappingTarget Magnitud entity);

    // ============================================
    // === OUTBOUND RESPONSES (Entity -> DTO) ===
    // ============================================
    
    // --- Categoria ---
    @Mapping(target = "idCategoriaSuperior", source = "categoriaSuperior.idcat")
    CategoriaResponseDTO toCategoriaResponse(Categoria entity);

    // --- Colores / Magnitudes / etc ---
    @Mapping(target = "id", source = "idcolor")
    @Mapping(target = "nombre", source = "nombreCol")
    CatalogoSimpleResponseDTO toColorResponse(Color entity);

    @Mapping(target = "id", source = "idseccion")
    @Mapping(target = "nombre", source = "nombreSec")
    CatalogoSimpleResponseDTO toSeccionResponse(Seccion entity);

    @Mapping(target = "id", source = "idproveedor")
    @Mapping(target = "nombre", source = "nombreProv")
    CatalogoSimpleResponseDTO toProveedorResponse(Proveedor entity);

    @Mapping(target = "id", source = "idmagnitud")
    @Mapping(target = "nombre", source = "nombreMag")
    CatalogoSimpleResponseDTO toMagnitudResponse(Magnitud entity);
}
