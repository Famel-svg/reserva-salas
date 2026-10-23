package com.famelsvg.reservasalas.common.error;

/** Violação de regra de negócio que a requisição não pode cumprir, como limite de reservas (HTTP 422). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
