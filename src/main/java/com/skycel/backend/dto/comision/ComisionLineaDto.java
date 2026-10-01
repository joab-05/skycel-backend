package com.skycel.backend.dto.comision;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila del reporte de comisiones: la de un encargado en el mes, o la de un vendedor por PayJoy en el mes. */
@Data
@Builder
public class ComisionLineaDto {

    private Integer idempleado;
    private String nombreEmpleado;
    private String nombreTienda;
    private Integer anio;
    private Integer mes;
    private BigDecimal ventaBase;
    private BigDecimal tasaAplicada;
    private BigDecimal comision;
    /** true si ya se marcó pagada (el monto queda congelado, no se vuelve a recalcular). */
    private boolean pagada;
    private LocalDate fechaPago;
    private String pagadaPor;
}
