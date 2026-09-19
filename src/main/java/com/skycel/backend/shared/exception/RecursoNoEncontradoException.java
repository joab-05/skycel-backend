package com.skycel.backend.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String recurso, Object id) {
        super(String.format("%s no encontrado con ID: %s", recurso, id));
        System.out.println("Recurso no encontrado: " + recurso+ " con ID: " + id);
    }
}
