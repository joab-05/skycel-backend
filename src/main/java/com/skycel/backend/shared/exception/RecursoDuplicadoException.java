package com.skycel.backend.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)  // HTTP 409 - Conflicto
public class RecursoDuplicadoException extends RuntimeException {

    public RecursoDuplicadoException(String recurso, String campo, Object valor) {
        super(String.format("Ya existe %s con %s: '%s'", recurso, campo, valor));
    }
}