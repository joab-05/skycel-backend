package com.skycel.backend.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

@Entity
@Table(name = "revinfo")
@RevisionEntity
public class CustomRevisionEntity extends DefaultRevisionEntity {
    // Extends DefaultRevisionEntity to bypass the HHH015007 Spring Boot 3 / Hibernate 6 
    // static metamodel field injection error on DefaultRevisionEntity_#class_
}
