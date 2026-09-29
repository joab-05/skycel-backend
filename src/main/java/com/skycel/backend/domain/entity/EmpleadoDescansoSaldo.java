package com.skycel.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * El "ancla" desde la que el sistema empieza a sumar días de descanso automáticamente para un empleado:
 * a partir de {@code fechaCorte} ya tenía {@code saldoInicial} acumulados. Se puede corregir cuando haga
 * falta (p. ej. al empezar a usar esto, o para reiniciar el conteo tras una revisión).
 */
@Entity
@Table(name = "empleado_descanso_saldo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpleadoDescansoSaldo {

    @Id
    @Column(name = "idempleado")
    private Integer idempleado;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "idempleado")
    private EmpleadoPerfil empleado;

    @Column(name = "saldo_inicial", nullable = false)
    private Integer saldoInicial;

    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;

    @UpdateTimestamp
    @Column(name = "actualizado_el")
    private LocalDateTime actualizadoEl;
}
