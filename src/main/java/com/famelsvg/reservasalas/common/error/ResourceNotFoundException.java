package com.famelsvg.reservasalas.common.error;

/** Recurso inexistente (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
