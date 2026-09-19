package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Audited
@Table(name = "producto_imei", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"imei"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoImei {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idproducto", nullable = false)
    private Producto producto;

    @Column(name = "imei", nullable = false, length = 20)
    private String imei;

    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private String estado = "DISPONIBLE"; // DISPONIBLE, VENDIDO, TRASPASADO, DEVUELTO

    // Precio de venta específico de ESTA unidad. Si es null, se usa el precio del
    // modelo (Producto.preciopub) — ver ProductoService.precioEfectivo(ProductoImei).
    @Column(name = "precio_venta_override", precision = 12, scale = 2)
    private java.math.BigDecimal precioVentaOverride;

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    @UpdateTimestamp
    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
