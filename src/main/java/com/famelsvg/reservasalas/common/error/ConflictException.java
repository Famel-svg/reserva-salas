package com.famelsvg.reservasalas.common.error;

/** Estado em conflito com o recurso atual, por exemplo sobreposição de horário (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
