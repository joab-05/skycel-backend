package com.skycel.backend.domain.mapper;

import com.skycel.backend.domain.dto.request.ProductoMasterRequestDTO;
import com.skycel.backend.domain.dto.request.ProductoRequestDTO;
import com.skycel.backend.domain.dto.response.ProductoMasterResponseDTO;
import com.skycel.backend.domain.dto.response.ProductoResponseDTO;
import com.skycel.backend.domain.entity.Producto;
import com.skycel.backend.domain.entity.ProductoMaster;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {CatalogoMapper.class})
public interface ProductoMapper {

    // --- ProductoMaster ---
    @Mapping(target = "idprodmaster", ignore = true)
    @Mapping(target = "nombreBase", source = "nombre")
    @Mapping(target = "especificaciones", source = "descripcion")
    @Mapping(target = "notaAdicional", ignore = true)
    @Mapping(target = "categoria", ignore = true) // Set in service
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "version", ignore = true)
    ProductoMaster toEntity(ProductoMasterRequestDTO dto);

    @Mapping(target = "idprodmaster", ignore = true)
    @Mapping(target = "nombreBase", source = "nombre")
    @Mapping(target = "especificaciones", source = "descripcion")
    @Mapping(target = "notaAdicional", ignore = true)
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntityFromDto(ProductoMasterRequestDTO dto, @MappingTarget ProductoMaster entity);

    @Mapping(target = "categoria", source = "categoria")
    ProductoMasterResponseDTO toResponse(ProductoMaster entity);


    // --- Producto (Stock) ---
    @Mapping(target = "idproducto", ignore = true)
    @Mapping(target = "productoMaster", ignore = true) // Set in service
    @Mapping(target = "tienda", ignore = true)         // Set in service
    @Mapping(target = "magnitud", ignore = true)       // Set in service
    @Mapping(target = "color", ignore = true)          // Set in service
    @Mapping(target = "proveedor", ignore = true)      // Set in service
    @Mapping(target = "seccion", ignore = true)        // Set in service
    @Mapping(target = "stock", source = "stock")
    @Mapping(target = "preciopro", source = "precioCompra")
    @Mapping(target = "preciopub", source = "precioVenta")
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "fechaIngreso", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "usuarioModificador", ignore = true)
    @Mapping(target = "rezagado", constant = "false")
    @Mapping(target = "publico", constant = "true")
    @Mapping(target = "version", ignore = true)
    Producto toProductoEntity(ProductoRequestDTO dto);

    @Mapping(target = "idproducto", ignore = true)
    @Mapping(target = "productoMaster", ignore = true)
    @Mapping(target = "tienda", ignore = true)
    @Mapping(target = "magnitud", ignore = true)
    @Mapping(target = "color", ignore = true)
    @Mapping(target = "proveedor", ignore = true)
    @Mapping(target = "seccion", ignore = true)
    @Mapping(target = "stock", source = "stock")
    @Mapping(target = "preciopro", source = "precioCompra")
    @Mapping(target = "preciopub", source = "precioVenta")
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "fechaIngreso", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    @Mapping(target = "usuarioModificador", ignore = true)
    @Mapping(target = "rezagado", ignore = true)
    @Mapping(target = "publico", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateProductoFromDto(ProductoRequestDTO dto, @MappingTarget Producto entity);

    @Mapping(target = "idProductoMaster", source = "productoMaster.idprodmaster")
    @Mapping(target = "nombreProductoMaster", source = "productoMaster.nombreBase")
    @Mapping(target = "color", source = "color")
    @Mapping(target = "magnitud", source = "magnitud")
    @Mapping(target = "proveedor", source = "proveedor")
    @Mapping(target = "seccion", source = "seccion")
    @Mapping(target = "tipo", source = "productoMaster.tipo")
    @Mapping(target = "marca", ignore = true)
    @Mapping(target = "modelo", ignore = true)
    @Mapping(target = "descripcion", ignore = true)
    @Mapping(target = "descripcion2", ignore = true)
    @Mapping(target = "compatibilidad", ignore = true)
    @Mapping(target = "tiempoEstimadoMin", ignore = true)
    @Mapping(target = "diasGarantia", ignore = true)
    @Mapping(target = "bajoStock", ignore = true)
    @Mapping(target = "imeisDisponibles", ignore = true)
    ProductoResponseDTO toProductoResponse(Producto entity);
}
