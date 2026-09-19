 package com.skycel.backend.dto.cpc;
 import lombok.Data;
 import java.math.BigDecimal;
 @Data
 public class PagoCuentaRequestDto {
     private BigDecimal monto;        // obligatorio
     private Byte       metodoPago;   // 1=Efectivo 2=Tarjeta 3=Transferencia 4=PayJoy
     private String     notas;
 }
