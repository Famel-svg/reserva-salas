package com.famelsvg.reservasalas.common.error;

/** Requisição semanticamente inválida (ex.: intervalo mal formado) -> 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
