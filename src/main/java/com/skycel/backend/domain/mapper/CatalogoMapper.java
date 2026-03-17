package com.skycel.backend.domain.mapper;

import com.skycel.backend.domain.dto.request.CategoriaRequestDTO;
import com.skycel.backend.domain.dto.request.CatalogoSimpleRequestDTO;
import com.skycel.backend.domain.dto.response.CategoriaResponseDTO;
import com.skycel.backend.domain.dto.response.CatalogoSimpleResponseDTO;
import com.skycel.backend.domain.dto.response.MagnitudResponseDTO;
import com.skycel.backend.domain.dto.response.ProveedorResponseDTO;
import com.skycel.backend.domain.dto.response.SeccionResponseDTO;
import com.skycel.backend.domain.dto.request.MagnitudRequestDTO;
import com.skycel.backend.domain.dto.request.ProveedorRequestDTO;
import com.skycel.backend.domain.dto.request.SeccionRequestDTO;
import com.skycel.backend.domain.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CatalogoMapper {

    // --- Categoria ---
    @Mapping(target = "idcat", ignore = true)
    @Mapping(target = "nombre", source = "nombreCat")
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "categoriaSuperior", ignore = true) // Set manually in service
    @Mapping(target = "subcategorias", ignore = true)
    Categoria toEntity(CategoriaRequestDTO dto);

    @Mapping(target = "idcat", ignore = true)
    @Mapping(target = "nombre", source = "nombreCat")
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "categoriaSuperior", ignore = true)
    @Mapping(target = "subcategorias", ignore = true)
    void updateEntityFromDto(CategoriaRequestDTO dto, @MappingTarget Categoria entity);


    // --- Color ---
    @Mapping(target = "idcolor", ignore = true)
    @Mapping(target = "activo", constant = "true")
    Color toColorEntity(CatalogoSimpleRequestDTO dto);

    @Mapping(target = "idcolor", ignore = true)
    @Mapping(target = "activo", ignore = true)
    void updateColorFromDto(CatalogoSimpleRequestDTO dto, @MappingTarget Color entity);


    // --- Seccion ---
    @Mapping(target = "idseccion", ignore = true)
    @Mapping(target = "tienda", ignore = true) // Set manually in service
    @Mapping(target = "activo", constant = "true")
    Seccion toSeccionEntity(SeccionRequestDTO dto);

    @Mapping(target = "idseccion", ignore = true)
    @Mapping(target = "tienda", ignore = true)
    @Mapping(target = "activo", ignore = true)
    void updateSeccionFromDto(SeccionRequestDTO dto, @MappingTarget Seccion entity);


    // --- Proveedor ---
    @Mapping(target = "idproveedor", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "version", ignore = true)
    Proveedor toProveedorEntity(ProveedorRequestDTO dto);

    @Mapping(target = "idproveedor", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateProveedorFromDto(ProveedorRequestDTO dto, @MappingTarget Proveedor entity);


    // --- Magnitud ---
    @Mapping(target = "idmagnitud", ignore = true)
    @Mapping(target = "activo", constant = "true")
    Magnitud toMagnitudEntity(MagnitudRequestDTO dto);

    @Mapping(target = "idmagnitud", ignore = true)
    @Mapping(target = "activo", ignore = true)
    void updateMagnitudFromDto(MagnitudRequestDTO dto, @MappingTarget Magnitud entity);

    // ============================================
    // === OUTBOUND RESPONSES (Entity -> DTO) ===
    // ============================================
    
    // --- Categoria ---
    @Mapping(target = "idCategoriaSuperior", source = "categoriaSuperior.idcat")
    CategoriaResponseDTO toCategoriaResponse(Categoria entity);

    // --- Colores / Magnitudes / etc ---
    @Mapping(target = "id", source = "idcolor")
    CatalogoSimpleResponseDTO toColorResponse(Color entity);

    @Mapping(target = "id", source = "idseccion")
    @Mapping(target = "codti", source = "tienda.codti")
    @Mapping(target = "nombreTienda", source = "tienda.nombre")
    SeccionResponseDTO toSeccionResponse(Seccion entity);

    @Mapping(target = "id", source = "idproveedor")
    ProveedorResponseDTO toProveedorResponse(Proveedor entity);

    @Mapping(target = "id", source = "idmagnitud")
    MagnitudResponseDTO toMagnitudResponse(Magnitud entity);
}
