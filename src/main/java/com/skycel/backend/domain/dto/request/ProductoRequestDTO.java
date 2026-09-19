package com.skycel.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductoRequestDTO {

    @Size(max = 25)
    private String codpro; // Opcional, si viene vacío se generará de manera automática.

    private java.util.List<String> imeis; // Opcional. Requerido sólo si el producto es de tipo CELULAR.

    // Alternativa a `imeis` cuando las unidades tienen datos propios (condición, costo, precio).
    // No se pueden enviar ambas listas a la vez.
    private java.util.List<UnidadEquipoDTO> unidades;

    private BigDecimal stockMinimo;    // Opcional: umbral de reposición (alerta de bajo stock). No aplica a servicios.

    // Atributos del artículo (solo se usan al crear el maestro, no si se reutiliza uno con idProductoMaster)
    private String  compatibilidad;    // ACCESORIO/SERVICIO: modelos con los que sirve (ej. "iPhone 13", "Universal")
    private Integer tiempoEstimadoMin; // SERVICIO: duración estimada en minutos
    private Integer diasGarantia;      // Días de garantía del artículo o servicio

    @NotNull(message = "La tienda es obligatoria")
    private Integer codti;              // ← antes hardcodeado en el service como 1

    @NotNull(message = "El stock inicial es obligatorio")
    private BigDecimal stock;

    @NotNull(message = "El precio de compra es obligatorio")
    private BigDecimal precioCompra;

    @NotNull(message = "El precio de venta es obligatorio")
    private BigDecimal precioVenta;

    private Short idColor;             // opcional

    private Short idMagnitud;          // opcional

    private Short idProveedor;         // opcional

    private Short idSeccion;           // opcional

    private Long idProductoMaster;     // opcional

    private String nombreMaster;       // Opcional: si se da, se usa tal cual (override manual).
                                        // Si no, el nombre se arma solo a partir de los campos
                                        // de abajo según el tipo — ver ProductoService.construirNombreBase.

    private String tipoMaster;         // Opcional. CELULAR, ACCESORIO, SERVICIO. Usado si se crea el maestro.

    private String categoriaMaster;    // Opcional. Nombre de la categoría/Tipo2. Usado si se crea el maestro.

    // Solo para tipo CELULAR (Equipo)
    private String marca;              // ej. "iPhone"
    private String modelo;             // ej. "17 Pro Max 8/256gb"

    // Solo para ACCESORIO y SERVICIO
    private String descripcion;        // ej. "AirPods Pro 2 Gen" / "Cambio de Pantalla"
    private String descripcion2;       // Solo SERVICIO: equipo al que aplica, ej. "Samsung A56 5G"
}
