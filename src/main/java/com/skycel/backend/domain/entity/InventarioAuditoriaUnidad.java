package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Un equipo (celular o tablet) que se escaneó en un inventario físico. Los equipos se cuentan por IMEI o serie, uno por uno;
 * al enviar el conteo a revisión, los disponibles del artículo que no se escanearon quedan como faltantes.
 */
@Entity
@Table(name = "inventario_auditoria_unidad",
        uniqueConstraints = @UniqueConstraint(name = "uk_inv_unidad", columnNames = {"idinventario", "imei"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventarioAuditoriaUnidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idinventario", nullable = false)
    private InventarioAuditoria inventarioAuditoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idprodmaster", nullable = false)
    private ProductoMaster productoMaster;

    @Column(name = "imei", nullable = false, length = 20)
    private String imei;

    /** true si al escanearlo estaba DISPONIBLE en esta sucursal; false si el sistema lo tenía en otro estado o en otra sucursal (un sobrante). */
    @Column(name = "en_sistema", nullable = false)
    private Boolean enSistema;
}
