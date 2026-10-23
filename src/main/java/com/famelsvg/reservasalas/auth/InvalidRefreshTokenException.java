package com.famelsvg.reservasalas.auth;

/** Refresh token inexistente, expirado ou já revogado; a mensagem é a mesma nos três casos. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token inválido");
    }
}
