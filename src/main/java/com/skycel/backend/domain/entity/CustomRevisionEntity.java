package com.skycel.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Entidad de revisión de Envers mapeada al DDL original de {@code revinfo}
 * (columnas {@code rev} y {@code revtstmp}), que es la que referencian las FK de las tablas *_aud.
 * No extiende DefaultRevisionEntity: esa clase usa las columnas {@code id}/{@code timestamp},
 * que no coinciden con las FK existentes y además dispara el error HHH015007 en Spring Boot 3 / Hibernate 6.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity
public class CustomRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "rev")
    private int rev;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long revtstmp;

    public int getRev() {
        return rev;
    }

    public void setRev(int rev) {
        this.rev = rev;
    }

    public long getRevtstmp() {
        return revtstmp;
    }

    public void setRevtstmp(long revtstmp) {
        this.revtstmp = revtstmp;
    }
}
