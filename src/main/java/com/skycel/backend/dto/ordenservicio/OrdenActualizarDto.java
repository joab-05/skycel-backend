package com.skycel.backend.dto.ordenservicio;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** PATCH de los datos de una orden abierta. Los campos que no se envían no cambian. */
@Data
public class OrdenActualizarDto {

    @Size(max = 60, message = "La marca no puede exceder los 60 caracteres")
    private String marca;

    @Size(max = 120, message = "El modelo no puede exceder los 120 caracteres")
    private String modelo;

    @Size(max = 20, message = "El IMEI o serie no puede exceder los 20 caracteres")
    private String imei;

    @Size(max = 255, message = "Los accesorios dejados no pueden exceder los 255 caracteres")
    private String accesoriosDejados;

    private String estadoFisico;

    private String fallaReportada;

    /** Lo que encontró el técnico. */
    private String diagnostico;

    private LocalDate fechaPromesa;
}
