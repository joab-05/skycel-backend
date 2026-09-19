 package com.skycel.backend.dto.cpc;

 import lombok.Builder; import lombok.Data;
 import java.math.BigDecimal; import java.time.LocalDate; import java.time.LocalDateTime;

 @Data @Builder
 public class CuentaPorCobrarResponseDto {

     Integer idcuenta; String noFactura;
     Integer idcliente; String nombreCliente;
     LocalDate fechaEmision; LocalDate fechaVencimiento;
     long diasVencimiento;         // negativo = vencida
     BigDecimal montoTotal; BigDecimal montoPagado; BigDecimal saldoPendiente;
     Byte estado; String estadoDisplay; String estadoColor;
     String observaciones; LocalDateTime fechaRegistro;
 }
