package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** Recepción de un equipo en el taller. */
@Data
public class OrdenServicioRequestDto {

    /** Tienda donde se recibe. Opcional: si se omite es la del usuario. */
    private Integer codti;

    @NotNull(message = "El cliente es obligatorio")
    private Integer idcliente;

    @NotBlank(message = "La marca del equipo es obligatoria")
    @Size(max = 60, message = "La marca no puede exceder los 60 caracteres")
    private String marca;

    @NotBlank(message = "El modelo del equipo es obligatorio")
    @Size(max = 120, message = "El modelo no puede exceder los 120 caracteres")
    private String modelo;

    /** IMEI o número de serie (5 a 20 caracteres alfanuméricos). Opcional. */
    @Size(max = 20, message = "El IMEI o serie no puede exceder los 20 caracteres")
    private String imei;

    /** Lo que el cliente deja junto con el equipo (chip, funda, cargador...). */
    @Size(max = 255, message = "Los accesorios dejados no pueden exceder los 255 caracteres")
    private String accesoriosDejados;

    /** Golpes, rayones o cualquier daño previo. */
    private String estadoFisico;

    @NotBlank(message = "La falla reportada es obligatoria")
    private String fallaReportada;

    /** Fecha prometida al cliente (no puede ser anterior a hoy). */
    private LocalDate fechaPromesa;

    /** Técnico asignado (usuario con rol TECNICO). Opcional: se puede asignar después. */
    private Integer idTecnico;

    /** Servicios y refacciones ya conocidos al recibir (presupuesto inicial). Opcional. */
    @Valid
    private List<OrdenLineaRequestDto> lineas;

    /** Anticipo que deja el cliente al recibir el equipo. Opcional. */
    @Valid
    private AnticipoRequestDto anticipo;
}
